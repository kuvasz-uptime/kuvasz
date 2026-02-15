package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDefaults
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.http.expectedHeadersAsMap
import com.kuvaszuptime.kuvasz.models.monitor.http.requestHeadersAsMap
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import kotlinx.coroutines.delay
import org.jooq.DSLContext

/**
 * These tests are a bit different from the others, because:
 * - it is not a MicronautTest, because we want to govern the ApplicationContext lifecycle manually
 * - the cases depend on each other to simulate the real-world changes of the configuration flow
 * - the DB cleanup is done after the whole test class and not after each test
 *
 * So take care when dealing with it, because it might break other tests too if not handled properly
 */
class AppBootstrappingHttpMonitorYamlConfigTest : StringSpec({

    var appContext: ApplicationContext? = null

    val monitorsAfterTheFirstStep = mutableListOf<HttpMonitorRecord>()
    val monitorsAfterTheSecondStep = mutableListOf<HttpMonitorRecord>()

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

    fun getCheckScheduler() = appContext?.getBean<HttpCheckScheduler>().shouldNotBeNull()
    fun getMonitorRepository() = appContext?.getBean<HttpMonitorRepository>().shouldNotBeNull()
    fun getAppConfig() = appContext?.getBean<AppConfig>().shouldNotBeNull()
    fun getMonitorActions() = appContext?.getBean<HttpMonitorActions>().shouldNotBeNull()

    /**
     * The whole test logic for the first step is reused, because we test that we get the same outcome in a later step,
     * no matter what happened before.
     */
    fun executeAndAssertTheFirstStep() {
        appContext = testAppContext("yaml-monitors", "full-integrations-setup")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // All the monitors in there should be added to the DB
        val monitorsInDb = monitorRepository.fetchAll() shouldHaveSize 3
        // Enabled monitors should be scheduled for uptime checks
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks shouldHaveSize 2
        // Enabled monitors with sslCheckEnabled should be scheduled for SSL checks
        val scheduledSSLChecks = checkScheduler.getScheduledSSLChecks()
        scheduledSSLChecks shouldHaveSize 1
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.url shouldBe "http://example.com"
            firstMonitor.uptimeCheckInterval shouldBe 120
            firstMonitor.enabled shouldBe false
            firstMonitor.sslCheckEnabled shouldBe true
            firstMonitor.requestMethod shouldBe HttpMethod.HEAD
            firstMonitor.latencyHistoryEnabled shouldBe false
            firstMonitor.forceNoCache shouldBe false
            firstMonitor.followRedirects shouldBe false
            firstMonitor.sslExpiryThreshold shouldBe 0
            firstMonitor.failureCountThreshold shouldBe 3

            scheduledUptimeChecks[firstMonitor.id].shouldBeNull()
            scheduledSSLChecks[firstMonitor.id].shouldBeNull()
        }

        monitorsInDb.forOne { secondMonitor ->
            secondMonitor.name shouldBe "test2"
            secondMonitor.url shouldBe "http://example.org"
            secondMonitor.uptimeCheckInterval shouldBe 60
            secondMonitor.enabled shouldBe HttpMonitorDefaults.MONITOR_ENABLED
            secondMonitor.sslCheckEnabled shouldBe HttpMonitorDefaults.SSL_CHECK_ENABLED
            secondMonitor.requestMethod shouldBe HttpMethod.valueOf(HttpMonitorDefaults.REQUEST_METHOD)
            secondMonitor.latencyHistoryEnabled shouldBe HttpMonitorDefaults.LATENCY_HISTORY_ENABLED
            secondMonitor.forceNoCache shouldBe HttpMonitorDefaults.FORCE_NO_CACHE
            secondMonitor.followRedirects shouldBe HttpMonitorDefaults.FOLLOW_REDIRECTS
            secondMonitor.sslExpiryThreshold shouldBe 10
            secondMonitor.integrations shouldBe arrayOf(
                IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")
            )
            scheduledUptimeChecks[secondMonitor.id].shouldNotBeNull()
            scheduledSSLChecks[secondMonitor.id].shouldBeNull()
        }

        monitorsInDb.forOne { thirdMonitor ->
            thirdMonitor.name shouldBe "test3"
            thirdMonitor.url shouldBe "http://example.net"
            thirdMonitor.uptimeCheckInterval shouldBe 120
            thirdMonitor.enabled shouldBe true
            thirdMonitor.sslCheckEnabled shouldBe true
            thirdMonitor.requestMethod shouldBe HttpMethod.GET
            thirdMonitor.latencyHistoryEnabled shouldBe true
            thirdMonitor.forceNoCache shouldBe false
            thirdMonitor.followRedirects shouldBe true
            thirdMonitor.sslExpiryThreshold shouldBe HttpMonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS

            scheduledUptimeChecks[thirdMonitor.id].shouldNotBeNull()
            scheduledSSLChecks[thirdMonitor.id].shouldNotBeNull()
        }
        // Saving the monitors from the DB to be able to check them later
        monitorsAfterTheFirstStep.addAll(monitorsInDb)
    }

    /**
     * A new YAML config is used again a totally fresh & clean instance, and the monitors from the config should be
     * imported & scheduled
     */
    "1. step: the app is started with a valid YAML config for the monitors" {
        executeAndAssertTheFirstStep()
    }

    /**
     * This test simulates a change in the YAML config, where the monitors are changed:
     * - one is removed
     * - one is added
     * - one is modified
     * - one left unchanged
     */
    "2. step: the app is restarted with some changes to the YAML configs" {
        // Waiting a whole second to make sure that the updatedAt timestamp is different from the createdAt timestamp
        delay(1000)

        appContext = testAppContext("yaml-monitors-changed")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // All the monitors in there should be added to the DB
        val monitorsInDb = monitorRepository.fetchAll() shouldHaveSize 3
        // Enabled monitors should be scheduled for uptime checks
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks shouldHaveSize 3
        // Enabled monitors with sslCheckEnabled should be scheduled for SSL checks
        val scheduledSSLChecks = checkScheduler.getScheduledSSLChecks()
        scheduledSSLChecks shouldHaveSize 1
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.url shouldBe "http://example.com"
            firstMonitor.uptimeCheckInterval shouldBe 120
            firstMonitor.enabled shouldBe true
            firstMonitor.sslCheckEnabled shouldBe true
            firstMonitor.requestMethod shouldBe HttpMethod.HEAD
            firstMonitor.latencyHistoryEnabled shouldBe false
            firstMonitor.forceNoCache shouldBe false
            firstMonitor.followRedirects shouldBe false
            firstMonitor.sslExpiryThreshold shouldBe 15
            firstMonitor.failureCountThreshold shouldBe 2
            firstMonitor.expectedStatusCodes shouldContainExactlyInAnyOrder arrayOf(200, 201)
            firstMonitor.expectedKeyword shouldBe "something"
            firstMonitor.expectedKeywordCaseSensitive shouldBe true
            firstMonitor.expectedKeywordNegated shouldBe true
            firstMonitor.responseTimeThresholdMillis shouldBe 500
            firstMonitor.requestHeadersAsMap() shouldContainExactly mapOf(
                "User-Agent" to "Mozilla/5.0",
                "X-Custom-Header" to "custom-value"
            )
            firstMonitor.expectedHeadersAsMap() shouldContainExactly mapOf(
                "Content-Type" to "application/json",
                "X-Example-Header" to "example-value"
            )
            firstMonitor.requestBody shouldBe "{\"key\": \"value\"}"
            firstMonitor.updatedAt.shouldNotBeNull() shouldBeAfter firstMonitor.createdAt

            scheduledUptimeChecks[firstMonitor.id].shouldNotBeNull()
            scheduledSSLChecks[firstMonitor.id].shouldNotBeNull()

            monitorsAfterTheFirstStep.single { it.name == firstMonitor.name }.id shouldBe firstMonitor.id
        }

        monitorsInDb.forOne { secondMonitor ->
            secondMonitor.name shouldBe "test2"
            secondMonitor.url shouldBe "http://example.org"
            secondMonitor.uptimeCheckInterval shouldBe 60
            secondMonitor.enabled shouldBe HttpMonitorDefaults.MONITOR_ENABLED
            secondMonitor.sslCheckEnabled shouldBe HttpMonitorDefaults.SSL_CHECK_ENABLED
            secondMonitor.requestMethod shouldBe HttpMethod.valueOf(HttpMonitorDefaults.REQUEST_METHOD)
            secondMonitor.latencyHistoryEnabled shouldBe HttpMonitorDefaults.LATENCY_HISTORY_ENABLED
            secondMonitor.forceNoCache shouldBe HttpMonitorDefaults.FORCE_NO_CACHE
            secondMonitor.followRedirects shouldBe HttpMonitorDefaults.FOLLOW_REDIRECTS
            secondMonitor.updatedAt.shouldNotBeNull() shouldBeAfter secondMonitor.createdAt

            scheduledUptimeChecks[secondMonitor.id].shouldNotBeNull()
            scheduledSSLChecks[secondMonitor.id].shouldBeNull()

            monitorsAfterTheFirstStep.single { it.name == secondMonitor.name }.id shouldBe secondMonitor.id
        }

        monitorsInDb.forOne { thirdMonitor ->
            thirdMonitor.name shouldBe "test4"
            thirdMonitor.url shouldBe "http://example.io"
            thirdMonitor.uptimeCheckInterval shouldBe 300
            thirdMonitor.enabled shouldBe true
            thirdMonitor.sslCheckEnabled shouldBe false
            thirdMonitor.requestMethod shouldBe HttpMethod.GET
            thirdMonitor.latencyHistoryEnabled shouldBe true
            thirdMonitor.forceNoCache shouldBe true
            thirdMonitor.followRedirects shouldBe true

            scheduledUptimeChecks[thirdMonitor.id].shouldNotBeNull()
            scheduledSSLChecks[thirdMonitor.id].shouldBeNull()
        }
        // Saving the monitors from the DB to be able to check them later
        monitorsAfterTheSecondStep.addAll(monitorsInDb)
    }

    /**
     * This test simulates a change in the YAML config, where the config property is empty. In this
     * case the app should retain the previously persisted monitors and their scheduled checks, which is essential
     * because accidentally misconfiguring the YAML config should not cause any data loss
     */
    "3. step: the app is restarted with an empty value in the YAML config for the monitors" {
        appContext = testAppContext("yaml-monitors-empty-value")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // The app config should be set to enable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe false
        // All the previously set up monitors should be still in there
        monitorRepository.fetchAll().shouldHaveSize(3).shouldContainExactlyInAnyOrder(monitorsAfterTheSecondStep)
        // The same scheduled checks should be present
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks.shouldHaveSize(3)
        val scheduledSSLChecks = checkScheduler.getScheduledSSLChecks()
        scheduledSSLChecks.shouldHaveSize(1)

        // Creating a monitor by hand during runtime that should be persisted & scheduled
        getMonitorActions().createMonitor(
            HttpMonitorCreateDto(
                name = "manual_monitor",
                url = "http://example.dev",
                uptimeCheckInterval = 300000,
                enabled = true,
                sslCheckEnabled = true,
            )
        )
        monitorRepository.fetchAll() shouldHaveSize 4
        checkScheduler.getScheduledUptimeChecks() shouldHaveSize 4
        checkScheduler.getScheduledSSLChecks() shouldHaveSize 2
    }

    "4. step: the app is restarted with no YAML config for HTTP monitors" {
        appContext = testAppContext("full-integrations-setup")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // The app config should be set to enable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe false
        // All the previously set up monitors should be still in there
        val monitorsInDb = monitorRepository.fetchAll().map { it.name }
        monitorsInDb
            .shouldHaveSize(4)
            .shouldContainExactlyInAnyOrder(monitorsAfterTheSecondStep.map { it.name } + "manual_monitor")

        // The same scheduled checks should be present
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks.shouldHaveSize(4)
        val scheduledSSLChecks = checkScheduler.getScheduledSSLChecks()
        scheduledSSLChecks.shouldHaveSize(2)
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
     * should delete all previously persisted monitors, because the user explicitly wants to have zero
     * monitors, and disable external writes against them.
     */
    "6. step: the app is started with an empty array in the YAML config for the monitors" {
        appContext = testAppContext("yaml-monitors-empty-array")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // No monitors should be present in the DB
        monitorRepository.fetchAll().shouldBeEmpty()
        // No scheduled checks should be present
        checkScheduler.getScheduledUptimeChecks() shouldHaveSize 0
        checkScheduler.getScheduledSSLChecks() shouldHaveSize 0
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe true
        getAppConfig().isPushMonitorExternalWriteDisabled() shouldBe false
    }

    /**
     * This test simulates a case where the YAML config is used, but one of the integrations is not present in the
     * integrations' config. In this case the app should throw an exception, and should not start up.
     */
    "7. step: the app started with some monitors in the YAML, but there is a non-existing integration on one of them" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("yaml-monitors-missing-integration", "full-integrations-setup")
        }

        ex.message shouldContain "Non-existing integration ID found: slack:non-existing."
    }
})
