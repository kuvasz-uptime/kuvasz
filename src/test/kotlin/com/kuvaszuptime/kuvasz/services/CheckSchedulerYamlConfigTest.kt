package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDefaults
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
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
class CheckSchedulerYamlConfigTest : StringSpec({

    var appContext: ApplicationContext? = null

    val monitorsAfterTheFirstStep = mutableListOf<MonitorRecord>()
    val monitorsAfterTheSecondStep = mutableListOf<MonitorRecord>()

    afterTest {
        // Stopping the app context after each test, so we can practically simulate the app restart
        appContext?.stop()
        appContext = null
    }

    afterSpec {
        // Doing a final manual cleanup after all tests to make sure that we don't leave any data behind that would
        // influence the consecutive tests
        val ephemeralAppContext = ApplicationContext.run()
        ephemeralAppContext.getBean<DSLContext>().resetDatabase()
        ephemeralAppContext.stop()
    }

    fun getCheckScheduler() = appContext?.getBean<CheckScheduler>().shouldNotBeNull()
    fun getMonitorRepository() = appContext?.getBean<MonitorRepository>().shouldNotBeNull()
    fun getAppConfig() = appContext?.getBean<AppConfig>().shouldNotBeNull()
    fun getMonitorCrudService() = appContext?.getBean<MonitorCrudService>().shouldNotBeNull()

    /**
     * The whole test logic for the first step is reused, because we test that we get the same outcome in a later step,
     * no matter what happened before.
     */
    fun executeAndAssertTheFirstStep() {
        appContext = ApplicationContext.run("test", "yaml-monitors")
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
        getAppConfig().isExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.url shouldBe "http://example.com"
            firstMonitor.uptimeCheckInterval shouldBe 120
            firstMonitor.enabled shouldBe false
            firstMonitor.sslCheckEnabled shouldBe true
            firstMonitor.pagerdutyIntegrationKey shouldBe "1234567890abcdef"
            firstMonitor.requestMethod shouldBe HttpMethod.HEAD
            firstMonitor.latencyHistoryEnabled shouldBe false
            firstMonitor.forceNoCache shouldBe false
            firstMonitor.followRedirects shouldBe false
            firstMonitor.sslExpiryThreshold shouldBe 0

            scheduledUptimeChecks[firstMonitor.id].shouldBeNull()
            scheduledSSLChecks[firstMonitor.id].shouldBeNull()
        }

        monitorsInDb.forOne { secondMonitor ->
            secondMonitor.name shouldBe "test2"
            secondMonitor.url shouldBe "http://example.org"
            secondMonitor.uptimeCheckInterval shouldBe 60
            secondMonitor.enabled shouldBe MonitorDefaults.MONITOR_ENABLED
            secondMonitor.sslCheckEnabled shouldBe MonitorDefaults.SSL_CHECK_ENABLED
            secondMonitor.pagerdutyIntegrationKey shouldBe null
            secondMonitor.requestMethod shouldBe HttpMethod.valueOf(MonitorDefaults.REQUEST_METHOD)
            secondMonitor.latencyHistoryEnabled shouldBe MonitorDefaults.LATENCY_HISTORY_ENABLED
            secondMonitor.forceNoCache shouldBe MonitorDefaults.FORCE_NO_CACHE
            secondMonitor.followRedirects shouldBe MonitorDefaults.FOLLOW_REDIRECTS
            secondMonitor.sslExpiryThreshold shouldBe 10

            scheduledUptimeChecks[secondMonitor.id].shouldNotBeNull()
            scheduledSSLChecks[secondMonitor.id].shouldBeNull()
        }

        monitorsInDb.forOne { thirdMonitor ->
            thirdMonitor.name shouldBe "test3"
            thirdMonitor.url shouldBe "http://example.net"
            thirdMonitor.uptimeCheckInterval shouldBe 120
            thirdMonitor.enabled shouldBe true
            thirdMonitor.sslCheckEnabled shouldBe true
            thirdMonitor.pagerdutyIntegrationKey shouldBe null
            thirdMonitor.requestMethod shouldBe HttpMethod.GET
            thirdMonitor.latencyHistoryEnabled shouldBe true
            thirdMonitor.forceNoCache shouldBe false
            thirdMonitor.followRedirects shouldBe true
            thirdMonitor.sslExpiryThreshold shouldBe MonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS

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

        appContext = ApplicationContext.run("test", "yaml-monitors-changed")
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
        getAppConfig().isExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.url shouldBe "http://example.com"
            firstMonitor.uptimeCheckInterval shouldBe 120
            firstMonitor.enabled shouldBe true
            firstMonitor.sslCheckEnabled shouldBe true
            firstMonitor.pagerdutyIntegrationKey shouldBe null
            firstMonitor.requestMethod shouldBe HttpMethod.HEAD
            firstMonitor.latencyHistoryEnabled shouldBe false
            firstMonitor.forceNoCache shouldBe false
            firstMonitor.followRedirects shouldBe false
            firstMonitor.sslExpiryThreshold shouldBe 15
            firstMonitor.updatedAt shouldBeAfter firstMonitor.createdAt

            scheduledUptimeChecks[firstMonitor.id].shouldNotBeNull()
            scheduledSSLChecks[firstMonitor.id].shouldNotBeNull()

            monitorsAfterTheFirstStep.single { it.name == firstMonitor.name }.id shouldBe firstMonitor.id
        }

        monitorsInDb.forOne { secondMonitor ->
            secondMonitor.name shouldBe "test2"
            secondMonitor.url shouldBe "http://example.org"
            secondMonitor.uptimeCheckInterval shouldBe 60
            secondMonitor.enabled shouldBe MonitorDefaults.MONITOR_ENABLED
            secondMonitor.sslCheckEnabled shouldBe MonitorDefaults.SSL_CHECK_ENABLED
            secondMonitor.pagerdutyIntegrationKey shouldBe null
            secondMonitor.requestMethod shouldBe HttpMethod.valueOf(MonitorDefaults.REQUEST_METHOD)
            secondMonitor.latencyHistoryEnabled shouldBe MonitorDefaults.LATENCY_HISTORY_ENABLED
            secondMonitor.forceNoCache shouldBe MonitorDefaults.FORCE_NO_CACHE
            secondMonitor.followRedirects shouldBe MonitorDefaults.FOLLOW_REDIRECTS
            secondMonitor.updatedAt shouldBeAfter secondMonitor.createdAt

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
            thirdMonitor.pagerdutyIntegrationKey shouldBe null
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
     * This test simulates a change in the YAML config, where the config is either not present or it's empty. In this
     * case the app should retain the previously persisted monitors and their scheduled checks, which is essential for 2
     * reasons:
     * - accidentally missing the YAML config should not cause any data loss
     * - this can be seen also as restoring a backup from a YAML file, in which case it should be totally normal that
     * after a successful import one would like to remove the YAML config and re-enable the external writes to them
     */
    "3. step: the app is restarted with an empty YAML config for the monitors" {
        appContext = ApplicationContext.run("test", "yaml-monitors-empty")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // The app config should be set to enable external writes against the monitors
        getAppConfig().isExternalWriteDisabled() shouldBe false
        // All the previously set up monitors should be still in there
        monitorRepository.fetchAll().shouldHaveSize(3).shouldContainExactlyInAnyOrder(monitorsAfterTheSecondStep)
        // The same scheduled checks should be present
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks.shouldHaveSize(3)
        val scheduledSSLChecks = checkScheduler.getScheduledSSLChecks()
        scheduledSSLChecks.shouldHaveSize(1)

        // Creating a monitor by hand during runtime that should be persisted & scheduled
        getMonitorCrudService().createMonitor(
            MonitorCreateDto(
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

    /**
     * Here we practically say: "I don't care what happened before, if there is a YAML config, use it as a
     * single-source-of-truth!"
     */
    "4. step: the initial YAML config is used again" {
        executeAndAssertTheFirstStep()
    }
})
