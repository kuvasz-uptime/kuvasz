package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.uitest.auth.AdminCredentials
import com.kuvaszuptime.kuvasz.uitest.pages.LoginPage
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import io.kotest.core.spec.style.StringSpec
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import org.jooq.DSLContext
import java.nio.file.Path

/**
 * Base class for the browser-driven specs. Concrete specs are annotated `@MicronautTest`, which boots the real app
 * against the shared Testcontainers Postgres and field-injects the [EmbeddedServer] / [DSLContext] below (the same
 * pattern as `DatabaseStringSpec`).
 *
 * Lifecycle: one [Browser] per spec; a fresh [BrowserContext] + [Page] per [newPage] call (tracing on); after each
 * test, failures dump a `trace.zip` + screenshot to `build/uiTest-artifacts/`, contexts are closed and the DB is wiped.
 *
 * The body lambda receiver is [UiTestSpec], so specs can use the [StringSpec] DSL *and* reach [baseUrl] / [newPage].
 */
abstract class UiTestSpec(body: UiTestSpec.() -> Unit = {}) : StringSpec() {

    @Inject
    lateinit var embeddedServer: EmbeddedServer

    @Inject
    lateinit var dslContext: DSLContext

    val baseUrl: String get() = embeddedServer.uri.toString()

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser

    // Lazily-captured admin session, reused across the spec's tests to avoid re-doing the form login every time.
    private var adminStorageState: String? = null

    private val openContexts = mutableListOf<TrackedContext>()

    private data class TrackedContext(val context: BrowserContext, val page: Page)

    /**
     * Whether the database is wiped after each test (the default, for isolation). Specs whose data comes from
     * YAML config — which the app imports only once at startup, so it can't be re-created between tests — turn this
     * off and rely on the [afterSpec] cleanup instead.
     */
    protected open val resetDatabaseAfterEachTest: Boolean = true

    init {
        beforeSpec {
            playwright = Playwright.create()
            browser = PlaywrightSupport.launchBrowser(playwright)
        }

        afterSpec {
            if (::browser.isInitialized) browser.close()
            if (::playwright.isInitialized) playwright.close()
            // Safety net: make sure no spec leaks rows into the shared DB, even one that skipped the per-test reset.
            runCatching { dslContext.resetDatabase() }
        }

        afterTest { (testCase, result) ->
            val failed = result.isErrorOrFailure
            openContexts.forEachIndexed { index, tracked ->
                if (failed) {
                    PlaywrightSupport.artifactsDir.mkdirs()
                    val base = "${testCase.name.name.sanitizedForFile()}-$index"
                    tracked.context.tracing().stop(
                        Tracing.StopOptions().setPath(artifactPath("$base-trace.zip"))
                    )
                    tracked.page.screenshot(
                        Page.ScreenshotOptions().setPath(artifactPath("$base.png")).setFullPage(true)
                    )
                } else {
                    tracked.context.tracing().stop()
                }
                tracked.context.close()
            }
            openContexts.clear()
            if (resetDatabaseAfterEachTest) dslContext.resetDatabase()
        }

        body()
    }

    /**
     * Opens a brand-new isolated [Page]. [authenticated] (the default) seeds the context with a cached admin session
     * so the page lands as a logged-in user; pass `false` to drive the unauthenticated flows (e.g. the login form).
     */
    fun newPage(authenticated: Boolean = true): Page {
        val options = Browser.NewContextOptions()
            .setBaseURL(baseUrl)
            .setViewportSize(PlaywrightSupport.VIEWPORT_WIDTH, PlaywrightSupport.VIEWPORT_HEIGHT)
        if (authenticated) {
            options.setStorageState(adminStorageState())
        }
        val context = browser.newContext(options)
        context.tracing().start(
            Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true)
        )
        val page = context.newPage()
        openContexts += TrackedContext(context, page)
        return page
    }

    /**
     * Performs the real form login once and caches the resulting `storageState` (JWT cookie). The cached state stays
     * valid across the per-test `resetDatabase()` calls because the admin credentials come from config, not the DB,
     * and the JWT is self-contained.
     */
    private fun adminStorageState(): String =
        adminStorageState ?: captureAdminStorageState().also { adminStorageState = it }

    private fun captureAdminStorageState(): String {
        val context = browser.newContext(Browser.NewContextOptions().setBaseURL(baseUrl))
        try {
            val page = context.newPage()
            LoginPage(page).loginAs(AdminCredentials.USERNAME, AdminCredentials.PASSWORD)
            return context.storageState()
        } finally {
            context.close()
        }
    }

    private fun artifactPath(fileName: String): Path =
        PlaywrightSupport.artifactsDir.resolve(fileName).toPath()

    private fun String.sanitizedForFile(): String = replace(Regex("[^A-Za-z0-9-_]+"), "_").take(MAX_FILE_NAME_LENGTH)

    companion object {
        private const val MAX_FILE_NAME_LENGTH = 80
    }
}
