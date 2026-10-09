package com.kuvaszuptime.kuvasz.uitest

import com.google.gson.JsonObject
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import java.io.File

/**
 * Lifecycle helpers around Playwright: how to launch the browser and where to write failure artifacts. The per-spec /
 * per-test orchestration lives in [UiTestSpec].
 */
object PlaywrightSupport {

    const val UI_TEST_ENV = "ui-test"
    const val VIEWPORT_WIDTH = 1440
    const val VIEWPORT_HEIGHT = 900

    private const val SLOW_MO_MS = 150.0
    private const val SLOW_CPU_THROTTLING_RATE = 4
    private const val SLOW_REQUEST_MIN_DELAY_MS = 200
    private const val SLOW_REQUEST_MAX_DELAY_MS = 1000

    val artifactsDir: File = File("build/uiTest-artifacts")

    // Headless by default; pass `-Dui.headed=true` to watch the run (with a small slow-mo) locally.
    private val headed: Boolean get() = System.getProperty("ui.headed", "false").toBoolean()

    // Emulates a slow machine to irone out the problems CI runs into: `-Dui.slow=true`
    val slow: Boolean get() = System.getProperty("ui.slow", "false").toBoolean()

    fun launchBrowser(playwright: Playwright): Browser =
        playwright.chromium().launch(
            BrowserType.LaunchOptions()
                .setHeadless(!headed)
                .setSlowMo(if (headed) SLOW_MO_MS else 0.0)
        )

    /**
     * Throttles the CPU of [page] and holds back every fetch and XHR (HTMX included) for a random while before it's
     * sent, so the responses arrive late and in a shuffled order.
     */
    fun emulateSlowMachine(context: BrowserContext, page: Page) {
        context.addInitScript(
            """
            (() => {
                const delay = () => new Promise(resolve => setTimeout(
                    resolve,
                    $SLOW_REQUEST_MIN_DELAY_MS + Math.random() * ${SLOW_REQUEST_MAX_DELAY_MS - SLOW_REQUEST_MIN_DELAY_MS},
                ));
                const originalFetch = window.fetch;
                window.fetch = (...args) => delay().then(() => originalFetch(...args));
                const originalAbort = XMLHttpRequest.prototype.abort;
                XMLHttpRequest.prototype.abort = function (...args) {
                    this.aborted = true;
                    return originalAbort.apply(this, args);
                };
                const originalSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function (...args) {
                    delay().then(() => this.aborted || originalSend.apply(this, args));
                };
            })();
            """.trimIndent()
        )
        context.newCDPSession(page).send(
            "Emulation.setCPUThrottlingRate",
            JsonObject().apply { addProperty("rate", SLOW_CPU_THROTTLING_RATE) },
        )
    }
}
