package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import kotlinx.coroutines.delay
import org.jooq.DSLContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * These tests are a bit different from the others, because:
 * - it is not a MicronautTest, because we want to govern the ApplicationContext lifecycle manually
 * - the cases depend on each other to simulate the real-world changes of the configuration flow
 * - the DB cleanup is done after the whole test class and not after each test
 *
 * So take care when dealing with it, because it might break other tests too if not handled properly
 */
class AppBootstrappingStatusPageYamlConfigTest : StringSpec({

    var appContext: ApplicationContext? = null

    val statusPagesAfterTheFirstStep = mutableListOf<StatusPageRecord>()
    val statusPagesAfterTheSecondStep = mutableListOf<StatusPageRecord>()

    afterTest {
        // Stopping the app context after each test, so we can practically simulate the app restart
        appContext?.stop()
        appContext = null
    }

    afterSpec {
        // Doing a final manual cleanup after all tests to make sure that we don't leave any data behind that would
        // influence the consecutive tests
        val ephemeralAppContext = testAppContext()
        ephemeralAppContext.getBean<DSLContext>().resetDatabase()
        ephemeralAppContext.stop()
    }

    fun getStatusPageRepo() = appContext?.getBean<StatusPageRepository>().shouldNotBeNull()
    fun getAppConfig() = appContext?.getBean<AppConfig>().shouldNotBeNull()

    /**
     * The whole test logic for the first step is reused, because we test that we get the same outcome in a later step,
     * no matter what happened before.
     */
    fun executeAndAssertTheFirstStep() {
        appContext = testAppContext(
            "yaml-monitors",
            "yaml-push-monitors",
            "status-pages",
            "full-integrations-setup",
        )
        val statusPageRepo = getStatusPageRepo()

        // All the status pages in there should be added to the DB
        val pagesInDb = statusPageRepo.fetchAll() shouldHaveSize 3
        // The app config should be set to disable external writes against the status pages
        getAppConfig().isStatusPageExternalWriteDisabled() shouldBe true

        pagesInDb.forOne { firstPage ->
            firstPage.slug shouldBe "status-page-1"
            firstPage.title shouldBe "Status Page 1"
            firstPage.customLogoUrl shouldBe "https://example.com/logo.png"
            firstPage.customFaviconUrl shouldBe "https://example.com/favicon.png"
            firstPage.public shouldBe true
            firstPage.monitors shouldContainExactlyInAnyOrder arrayOf(
                MonitorID(MonitorType.HTTP_SSL, "test1"),
                MonitorID(MonitorType.HTTP_SSL, "test2"),
                MonitorID(MonitorType.PUSH, "test1"),
            )
        }

        pagesInDb.forOne { secondPage ->
            secondPage.slug shouldBe "status-page-2"
            secondPage.title shouldBe "Status Page 2"
            secondPage.public shouldBe false
            secondPage.monitors shouldContainExactlyInAnyOrder arrayOf(
                MonitorID(MonitorType.HTTP_SSL, "test1"),
            )
        }

        pagesInDb.forOne { thirdPage ->
            thirdPage.slug shouldBe "status-page-3"
            thirdPage.title shouldBe "Status Page 3"
            thirdPage.public shouldBe false
            thirdPage.monitors shouldContainExactlyInAnyOrder arrayOf(
                MonitorID(MonitorType.HTTP_SSL, "test3"),
                MonitorID(MonitorType.HTTP_SSL, "test1"),
                MonitorID(MonitorType.PUSH, "test2"),
            )
        }
        // Saving the monitors from the DB to be able to check them later
        statusPagesAfterTheFirstStep.addAll(pagesInDb)
    }

    /**
     * A new YAML config is used again a totally fresh & clean instance, and the pages from the config should be
     * imported
     */
    "1. step: the app is started with a valid YAML config for the status pages" {
        executeAndAssertTheFirstStep()
    }

    /**
     * This test simulates a change in the YAML config, where the pages are changed:
     * - one is removed
     * - one is added
     * - one is modified
     * - one left unchanged
     */
    "2. step: the app is restarted with some changes to the YAML configs" {
        // Waiting a whole second to make sure that the updatedAt timestamp is different from the createdAt timestamp
        delay(1000.milliseconds)

        appContext = testAppContext(
            "yaml-monitors",
            "status-pages-changed",
            "full-integrations-setup",
        )
        val statusPageRepo = getStatusPageRepo()

        // All the pages in there should be added to the DB
        val pagesInDb = statusPageRepo.fetchAll() shouldHaveSize 3
        // The app config should be set to disable external writes against the pages
        getAppConfig().isStatusPageExternalWriteDisabled() shouldBe true

        pagesInDb.forOne { updatedPage ->
            updatedPage.slug shouldBe "status-page-1"
            updatedPage.title shouldBe "Status Page Updated"
            updatedPage.customLogoUrl shouldBe "https://example.com/logo2.png"
            updatedPage.customFaviconUrl shouldBe "https://example.com/favicon2.png"
            updatedPage.public shouldBe false
            updatedPage.monitors shouldContainExactlyInAnyOrder arrayOf(
                MonitorID(MonitorType.HTTP_SSL, "test1"),
                MonitorID(MonitorType.HTTP_SSL, "test3"),
            )
            updatedPage.updatedAt shouldBeAfter updatedPage.createdAt

            statusPagesAfterTheFirstStep.single { it.slug == updatedPage.slug }.id shouldBe updatedPage.id
        }

        pagesInDb.forOne { untouchedPage ->
            untouchedPage.title shouldBe "Status Page 3"
            untouchedPage.slug shouldBe "status-page-3"
            untouchedPage.monitors shouldContainExactlyInAnyOrder arrayOf(
                MonitorID(MonitorType.HTTP_SSL, "test3"),
                MonitorID(MonitorType.HTTP_SSL, "test1"),
            )
            untouchedPage.public shouldBe false
            untouchedPage.updatedAt shouldBeAfter untouchedPage.createdAt

            statusPagesAfterTheFirstStep.single { it.slug == untouchedPage.slug }.id shouldBe untouchedPage.id
        }

        pagesInDb.forOne { newPage ->
            newPage.slug shouldBe "status-page-4"
            newPage.title shouldBe "Status Page 4"
            newPage.public shouldBe false
            newPage.monitors shouldContainExactlyInAnyOrder arrayOf(
                MonitorID(MonitorType.HTTP_SSL, "test1"),
            )
            newPage.createdAt shouldBe newPage.updatedAt
        }
        // Saving the page from the DB to be able to check them later
        statusPagesAfterTheSecondStep.addAll(pagesInDb)
    }

    /**
     * This test simulates a change in the YAML config, where the config property is empty. In this
     * case the app should retain the previously persisted status pages, which is essential
     * because accidentally misconfiguring the YAML config should not cause any data loss
     */
    "3. step: the app is restarted with an empty value in the YAML config for the status pages" {
        appContext = testAppContext(
            "yaml-monitors",
            "status-pages-empty-value",
            "full-integrations-setup",
        )
        val statusPagesRepo = getStatusPageRepo()

        // The app config should be set to enable external writes against the pages
        getAppConfig().isStatusPageExternalWriteDisabled() shouldBe false
        // All the previously set up pages should be still in there
        val pages = statusPagesRepo.fetchAll()
        pages.shouldHaveSize(3).shouldContainExactlyInAnyOrder(statusPagesAfterTheSecondStep)

        // Creating a page by hand during runtime that should be persisted
        statusPagesRepo.returningInsert(
            StatusPageRecord().apply {
                slug = "manual_page"
                title = "Manual Page"
                public = true
                monitors = listOf(
                    MonitorID(MonitorType.HTTP_SSL, "test1"),
                    MonitorID(MonitorType.HTTP_SSL, "test4"),
                ).toTypedArray()
            }
        )
        statusPagesRepo.fetchAll() shouldHaveSize 4
    }

    "4. step: the app is restarted with no YAML config for status pages" {
        appContext = testAppContext(
            "yaml-monitors",
            // No status-pages config
            "full-integrations-setup",
        )
        val statusPagesRepo = getStatusPageRepo()

        // The app config should be set to enable external writes against the pages
        getAppConfig().isStatusPageExternalWriteDisabled() shouldBe false
        // All the previously set up pages should be still in there, including the manually created one
        val pages = statusPagesRepo.fetchAll()
        pages.shouldHaveSize(4)
        pages.map { it.slug } shouldContainExactlyInAnyOrder listOf(
            "status-page-1",
            "status-page-3",
            "status-page-4",
            "manual_page",
        )
    }

    /**
     * Here we practically say: "I don't care what happened before, if there is a YAML config, use it as a
     * single-source-of-truth!"
     */
    "5. step: the initial YAML config is used again" {
        executeAndAssertTheFirstStep()
    }

    /**
     * This test simulates a case where the YAML config is used, but with an empty array. In this case the app
     * should delete all previously persisted status pages, because the user explicitly wants to have zero
     * status pages, and disable external writes against them.
     */
    "6. step: the app started with an empty array for the status pages in the YAML" {
        appContext = testAppContext(
            "yaml-monitors",
            "status-pages-empty-array",
            "full-integrations-setup",
        )
        val statusPageRepo = getStatusPageRepo()

        // The app config should be set to disable external writes against the pages
        getAppConfig().isStatusPageExternalWriteDisabled() shouldBe true
        // All the previously set up pages should be deleted, because an empty array was provided
        statusPageRepo.fetchAll().shouldBeEmpty()
    }

    /**
     * This test simulates a case where the YAML config is used, but one of the monitors is not present in the
     * monitors' config. In this case the app should ignore the missing monitor.
     */
    "7. step: the app started with some status pages in the YAML, but there is a non-existing monitor on one of them" {
        shouldNotThrowAny {
            testAppContext(
                "yaml-monitors",
                "status-pages-missing-monitor",
                "full-integrations-setup",
            )
        }
    }
})
