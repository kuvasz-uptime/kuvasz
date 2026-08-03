package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorDefaults
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.services.check.dns.DnsCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.dns.DnsMonitorActions
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
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
class AppBootstrappingDnsMonitorYamlConfigTest : StringSpec({

    var appContext: ApplicationContext? = null

    val monitorsAfterTheFirstStep = mutableListOf<DnsMonitorRecord>()
    val monitorsAfterTheSecondStep = mutableListOf<DnsMonitorRecord>()

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

    fun getCheckScheduler() = appContext?.getBean<DnsCheckScheduler>().shouldNotBeNull()
    fun getMonitorRepository() = appContext?.getBean<DnsMonitorRepository>().shouldNotBeNull()
    fun getAppConfig() = appContext?.getBean<AppConfig>().shouldNotBeNull()
    fun getMonitorActions() = appContext?.getBean<DnsMonitorActions>().shouldNotBeNull()

    /**
     * The whole test logic for the first step is reused, because we test that we get the same outcome in a later step,
     * no matter what happened before.
     */
    fun executeAndAssertTheFirstStep() {
        appContext = testAppContext("yaml-dns-monitors", "full-integrations-setup")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // All the monitors in there should be added to the DB
        val monitorsInDb = monitorRepository.fetchAll() shouldHaveSize 3
        // Enabled monitors should be scheduled for uptime checks
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks shouldHaveSize 2
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe false
        getAppConfig().isDnsMonitorExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.host shouldBe "example.com"
            firstMonitor.uptimeCheckInterval shouldBe 120
            firstMonitor.enabled shouldBe false
            firstMonitor.timeoutMs shouldBe 10000
            firstMonitor.latencyThresholdMs shouldBe 250
            firstMonitor.failureCountThreshold shouldBe 3
            firstMonitor.integrations shouldBe emptyArray()

            scheduledUptimeChecks[firstMonitor.id].shouldBeNull()
        }

        monitorsInDb.forOne { secondMonitor ->
            secondMonitor.name shouldBe "test2"
            secondMonitor.host shouldBe "kuvasz-uptime.dev"
            secondMonitor.uptimeCheckInterval shouldBe 60
            secondMonitor.enabled shouldBe DnsMonitorDefaults.MONITOR_ENABLED
            secondMonitor.timeoutMs shouldBe DnsMonitorDefaults.TIMEOUT_MS
            secondMonitor.latencyThresholdMs.shouldBeNull()
            secondMonitor.failureCountThreshold shouldBe DnsMonitorDefaults.FAILURE_COUNT_THRESHOLD
            secondMonitor.integrations shouldBe arrayOf(
                IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")
            )

            scheduledUptimeChecks[secondMonitor.id].shouldNotBeNull()
        }

        monitorsInDb.forOne { thirdMonitor ->
            thirdMonitor.name shouldBe "test3"
            thirdMonitor.host shouldBe "mail.example.com"
            thirdMonitor.resolverHost shouldBe "1.1.1.1"
            thirdMonitor.resolverPort shouldBe 5353
            thirdMonitor.transport shouldBe DnsTransport.TCP
            thirdMonitor.expectedResponseCode shouldBe DnsResponseCode.NXDOMAIN
            thirdMonitor.driftDetectionEnabled shouldBe true
            thirdMonitor.driftRecordTypes.toList() shouldBe listOf(DnsRecordType.NS, DnsRecordType.MX)
            thirdMonitor.uptimeCheckInterval shouldBe 120
            thirdMonitor.enabled shouldBe true
            thirdMonitor.timeoutMs shouldBe DnsMonitorDefaults.TIMEOUT_MS
            thirdMonitor.latencyThresholdMs.shouldBeNull()

            scheduledUptimeChecks[thirdMonitor.id].shouldNotBeNull()
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
        delay(1000.milliseconds)

        appContext = testAppContext("yaml-dns-monitors-changed", "full-integrations-setup")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // All the monitors in there should be added to the DB
        val monitorsInDb = monitorRepository.fetchAll() shouldHaveSize 3
        // Enabled monitors should be scheduled for uptime checks
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks shouldHaveSize 2
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isDnsMonitorExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.host shouldBe "example.org"
            firstMonitor.uptimeCheckInterval shouldBe 180
            firstMonitor.enabled shouldBe true
            firstMonitor.timeoutMs shouldBe 5000
            firstMonitor.latencyThresholdMs shouldBe 500
            firstMonitor.failureCountThreshold shouldBe 2
            firstMonitor.integrations shouldBe emptyArray()
            firstMonitor.updatedAt.shouldNotBeNull() shouldBeAfter firstMonitor.createdAt

            scheduledUptimeChecks[firstMonitor.id].shouldNotBeNull()

            monitorsAfterTheFirstStep.single { it.name == firstMonitor.name }.id shouldBe firstMonitor.id
        }

        monitorsInDb.forOne { secondMonitor ->
            secondMonitor.name shouldBe "test2"
            secondMonitor.host shouldBe "kuvasz-uptime.dev"
            secondMonitor.uptimeCheckInterval shouldBe 60
            secondMonitor.enabled shouldBe DnsMonitorDefaults.MONITOR_ENABLED
            secondMonitor.integrations shouldBe emptyArray()
            secondMonitor.updatedAt.shouldNotBeNull() shouldBeAfter secondMonitor.createdAt

            scheduledUptimeChecks[secondMonitor.id].shouldNotBeNull()

            monitorsAfterTheFirstStep.single { it.name == secondMonitor.name }.id shouldBe secondMonitor.id
        }

        monitorsInDb.forOne { thirdMonitor ->
            thirdMonitor.name shouldBe "test4"
            thirdMonitor.host shouldBe "other.example.com"
            thirdMonitor.uptimeCheckInterval shouldBe 300
            thirdMonitor.enabled shouldBe false
            thirdMonitor.timeoutMs shouldBe DnsMonitorDefaults.TIMEOUT_MS
            thirdMonitor.latencyThresholdMs.shouldBeNull()

            scheduledUptimeChecks[thirdMonitor.id].shouldBeNull()
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
        appContext = testAppContext("yaml-dns-monitors-empty-value")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // The app config should be set to enable external writes against the monitors
        getAppConfig().isDnsMonitorExternalWriteDisabled() shouldBe false
        // All the previously set up monitors should be still in there
        monitorRepository.fetchAll().shouldHaveSize(3).shouldContainExactlyInAnyOrder(monitorsAfterTheSecondStep)
        // The same scheduled checks should be present
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks.shouldHaveSize(2)

        // Creating a monitor by hand during runtime that should be persisted & scheduled
        getMonitorActions().createMonitor(
            DnsMonitorCreateDto(
                name = "manual_monitor",
                host = "manual.example.com",
                uptimeCheckInterval = 300,
                enabled = true,
            )
        )
        monitorRepository.fetchAll() shouldHaveSize 4
        checkScheduler.getScheduledUptimeChecks() shouldHaveSize 3
    }

    "4. step: the app is restarted with no YAML config for DNS monitors" {
        appContext = testAppContext("full-integrations-setup")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // The app config should be set to enable external writes against the monitors
        getAppConfig().isDnsMonitorExternalWriteDisabled() shouldBe false
        // All the previously set up monitors should be still in there
        val monitorsInDb = monitorRepository.fetchAll().map { it.name }
        monitorsInDb
            .shouldHaveSize(4)
            .shouldContainExactlyInAnyOrder(monitorsAfterTheSecondStep.map { it.name } + "manual_monitor")

        // The same scheduled checks should be present
        checkScheduler.getScheduledUptimeChecks().shouldHaveSize(3)
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
        appContext = testAppContext("yaml-dns-monitors-empty-array")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // No monitors should be present in the DB
        monitorRepository.fetchAll().shouldBeEmpty()
        // No scheduled checks should be present
        checkScheduler.getScheduledUptimeChecks() shouldHaveSize 0
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isDnsMonitorExternalWriteDisabled() shouldBe true
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe false
    }

    /**
     * This test simulates a case where the YAML config is used, but one of the integrations is not present in the
     * integrations' config. In this case the app should throw an exception, and should not start up.
     */
    "7. step: the app started with some monitors in the YAML, but there is a non-existing integration on one of them" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("yaml-dns-monitors-missing-integration", "full-integrations-setup")
        }

        ex.message shouldContain "Non-existing integration ID found: slack:non-existing."
    }
})
