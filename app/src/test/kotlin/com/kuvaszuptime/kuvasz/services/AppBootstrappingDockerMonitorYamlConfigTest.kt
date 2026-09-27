package com.kuvaszuptime.kuvasz.services

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorDefaults
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.services.check.docker.DockerCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.docker.DockerMonitorActions
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
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import kotlinx.coroutines.delay
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

/**
 * These tests are a bit different from the others, because:
 * - it is not a MicronautTest, because we want to govern the ApplicationContext lifecycle manually
 * - the cases depend on each other to simulate the real-world changes of the configuration flow
 * - the DB cleanup is done after the whole test class and not after each test
 *
 * So take care when dealing with it, because it might break other tests too if not handled properly
 */
class AppBootstrappingDockerMonitorYamlConfigTest : StringSpec({

    var appContext: ApplicationContext? = null

    val monitorsAfterTheFirstStep = mutableListOf<DockerMonitorRecord>()
    val monitorsAfterTheSecondStep = mutableListOf<DockerMonitorRecord>()

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

    fun getCheckScheduler() = appContext?.getBean<DockerCheckScheduler>().shouldNotBeNull()
    fun getMonitorRepository() = appContext?.getBean<DockerMonitorRepository>().shouldNotBeNull()
    fun getAppConfig() = appContext?.getBean<AppConfig>().shouldNotBeNull()
    fun getMonitorActions() = appContext?.getBean<DockerMonitorActions>().shouldNotBeNull()

    /**
     * The whole test logic for the first step is reused, because we test that we get the same outcome in a later step,
     * no matter what happened before.
     */
    fun executeAndAssertTheFirstStep() {
        appContext = testAppContext("yaml-docker-monitors", "full-integrations-setup", "docker-hosts")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // All the monitors in there should be added to the DB
        val monitorsInDb = monitorRepository.fetchAll() shouldHaveSize 3
        // Enabled monitors should be scheduled for uptime checks
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks shouldHaveSize 2
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe false
        getAppConfig().isDockerMonitorExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.dockerHost shouldBe "local"
            firstMonitor.container shouldBe "my-app"
            firstMonitor.uptimeCheckInterval shouldBe 120
            firstMonitor.enabled shouldBe false
            firstMonitor.timeoutMs shouldBe 10000
            firstMonitor.failureCountThreshold shouldBe 3
            firstMonitor.integrations shouldBe emptyArray()

            scheduledUptimeChecks[firstMonitor.id].shouldBeNull()
        }

        monitorsInDb.forOne { secondMonitor ->
            secondMonitor.name shouldBe "test2"
            secondMonitor.ignoreConnectivityCheck shouldBe DockerMonitorDefaults.IGNORE_CONNECTIVITY_CHECK
            secondMonitor.dockerHost shouldBe "local"
            secondMonitor.container shouldBe "8dfafdbc3a40"
            secondMonitor.uptimeCheckInterval shouldBe 60
            secondMonitor.enabled shouldBe DockerMonitorDefaults.MONITOR_ENABLED
            secondMonitor.timeoutMs shouldBe DockerMonitorDefaults.TIMEOUT_MS
            secondMonitor.failureCountThreshold shouldBe DockerMonitorDefaults.FAILURE_COUNT_THRESHOLD
            secondMonitor.integrations shouldBe arrayOf(
                IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")
            )

            scheduledUptimeChecks[secondMonitor.id].shouldNotBeNull()
        }

        monitorsInDb.forOne { thirdMonitor ->
            thirdMonitor.name shouldBe "test3"
            // Explicitly turned off in the YAML, as opposed to the other two which take the default
            thirdMonitor.ignoreConnectivityCheck shouldBe false
            thirdMonitor.dockerHost shouldBe "vps-1"
            thirdMonitor.container shouldBe "worker"
            thirdMonitor.uptimeCheckInterval shouldBe 120
            thirdMonitor.enabled shouldBe true
            thirdMonitor.timeoutMs shouldBe DockerMonitorDefaults.TIMEOUT_MS

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

        appContext = testAppContext("yaml-docker-monitors-changed", "full-integrations-setup", "docker-hosts")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // All the monitors in there should be added to the DB
        val monitorsInDb = monitorRepository.fetchAll() shouldHaveSize 3
        // Enabled monitors should be scheduled for uptime checks
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks shouldHaveSize 2
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isDockerMonitorExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.dockerHost shouldBe "local"
            firstMonitor.container shouldBe "renamed-app"
            firstMonitor.uptimeCheckInterval shouldBe 180
            firstMonitor.enabled shouldBe true
            firstMonitor.timeoutMs shouldBe 5000
            firstMonitor.failureCountThreshold shouldBe 2
            firstMonitor.integrations shouldBe emptyArray()
            firstMonitor.updatedAt.shouldNotBeNull() shouldBeAfter firstMonitor.createdAt

            scheduledUptimeChecks[firstMonitor.id].shouldNotBeNull()

            monitorsAfterTheFirstStep.single { it.name == firstMonitor.name }.id shouldBe firstMonitor.id
        }

        monitorsInDb.forOne { secondMonitor ->
            secondMonitor.name shouldBe "test2"
            secondMonitor.dockerHost shouldBe "local"
            secondMonitor.container shouldBe "8dfafdbc3a40"
            secondMonitor.uptimeCheckInterval shouldBe 60
            secondMonitor.enabled shouldBe DockerMonitorDefaults.MONITOR_ENABLED
            secondMonitor.integrations shouldBe emptyArray()
            secondMonitor.updatedAt.shouldNotBeNull() shouldBeAfter secondMonitor.createdAt

            scheduledUptimeChecks[secondMonitor.id].shouldNotBeNull()

            monitorsAfterTheFirstStep.single { it.name == secondMonitor.name }.id shouldBe secondMonitor.id
        }

        monitorsInDb.forOne { thirdMonitor ->
            thirdMonitor.name shouldBe "test4"
            thirdMonitor.dockerHost shouldBe "vps-1"
            thirdMonitor.container shouldBe "batch-job"
            thirdMonitor.uptimeCheckInterval shouldBe 300
            thirdMonitor.enabled shouldBe false
            thirdMonitor.timeoutMs shouldBe DockerMonitorDefaults.TIMEOUT_MS

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
        appContext = testAppContext("yaml-docker-monitors-empty-value", "docker-hosts")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // The app config should be set to enable external writes against the monitors
        getAppConfig().isDockerMonitorExternalWriteDisabled() shouldBe false
        // All the previously set up monitors should be still in there
        monitorRepository.fetchAll().shouldHaveSize(3).shouldContainExactlyInAnyOrder(monitorsAfterTheSecondStep)
        // The same scheduled checks should be present
        val scheduledUptimeChecks = checkScheduler.getScheduledUptimeChecks()
        scheduledUptimeChecks.shouldHaveSize(2)

        // Creating a monitor by hand during runtime that should be persisted & scheduled
        getMonitorActions().createMonitor(
            DockerMonitorCreateDto(
                name = "manual_monitor",
                dockerHost = "local",
                container = "manual-container",
                uptimeCheckInterval = 300,
                enabled = true,
            )
        )
        monitorRepository.fetchAll() shouldHaveSize 4
        checkScheduler.getScheduledUptimeChecks() shouldHaveSize 3
    }

    "4. step: the app is restarted with no YAML config for DOCKER monitors" {
        appContext = testAppContext("full-integrations-setup")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // The app config should be set to enable external writes against the monitors
        getAppConfig().isDockerMonitorExternalWriteDisabled() shouldBe false
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
        appContext = testAppContext("yaml-docker-monitors-empty-array")
        val checkScheduler = getCheckScheduler()
        val monitorRepository = getMonitorRepository()

        // No monitors should be present in the DB
        monitorRepository.fetchAll().shouldBeEmpty()
        // No scheduled checks should be present
        checkScheduler.getScheduledUptimeChecks() shouldHaveSize 0
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isDockerMonitorExternalWriteDisabled() shouldBe true
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe false
    }

    /**
     * This test simulates a case where the YAML config is used, but one of the integrations is not present in the
     * integrations' config. In this case the app should throw an exception, and should not start up.
     */
    "7. step: the app started with some monitors in the YAML, but there is a non-existing integration on one of them" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("yaml-docker-monitors-missing-integration", "full-integrations-setup", "docker-hosts")
        }

        ex.message shouldContain "Non-existing integration ID found: slack:non-existing."
    }

    "8. step: the initial YAML config is used again" {
        executeAndAssertTheFirstStep()
    }

    /**
     * The very same YAML snippet is re-imported as an update of the existing monitors, so the one whose host was
     * removed from the config in the meantime has to be kept - its checks report the dangling reference.
     */
    "9. step: the app is restarted with the same YAML config, but one of the Docker hosts was removed" {
        val logs = ListAppender<ILoggingEvent>().apply { start() }
        val bootstrapperLogger = LoggerFactory.getLogger(AppBootstrapper::class.java) as Logger
        bootstrapperLogger.addAppender(logs)
        try {
            appContext = testAppContext("yaml-docker-monitors", "full-integrations-setup", "docker-hosts-without-vps-1")
        } finally {
            bootstrapperLogger.detachAppender(logs)
        }
        val monitorRepository = getMonitorRepository()

        val monitorsInDb = monitorRepository.fetchAll() shouldHaveSize 3
        val danglingMonitor = monitorsInDb.single { it.name == "test3" }
        danglingMonitor.dockerHost shouldBe "vps-1"
        // The first step ran three times by now, and it is the latest run's monitor that has to be kept
        danglingMonitor.id shouldBe monitorsAfterTheFirstStep.last { it.name == "test3" }.id
        // It stays scheduled, so its checks can report the missing host
        getCheckScheduler().getScheduledUptimeChecks()[danglingMonitor.id].shouldNotBeNull()

        logs.list.map { it.formattedMessage }.forOne { message ->
            message shouldContain "reference a Docker host that is not configured"
            message shouldContain "test3 (ID: ${danglingMonitor.id}, host: vps-1)"
            message shouldNotContain "test1"
        }
    }

    "10. step: the app is restarted with a new monitor in the YAML config on a Docker host that is not configured" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("yaml-docker-monitors-changed", "full-integrations-setup", "docker-hosts-without-vps-1")
        }

        ex.message shouldContain "Non-existing Docker host found: vps-1."
    }
})
