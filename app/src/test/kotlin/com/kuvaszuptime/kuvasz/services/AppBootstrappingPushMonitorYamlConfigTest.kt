package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDefaults
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
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
class AppBootstrappingPushMonitorYamlConfigTest : StringSpec({

    var appContext: ApplicationContext? = null

    val monitorsAfterTheFirstStep = mutableListOf<PushMonitorRecord>()
    val monitorsAfterTheSecondStep = mutableListOf<PushMonitorRecord>()

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

    fun getMonitorRepository() = appContext?.getBean<PushMonitorRepository>().shouldNotBeNull()
    fun getAppConfig() = appContext?.getBean<AppConfig>().shouldNotBeNull()

    /**
     * The whole test logic for the first step is reused, because we test that we get the same outcome in a later step,
     * no matter what happened before.
     */
    fun executeAndAssertTheFirstStep() {
        appContext = testAppContext("yaml-push-monitors", "full-integrations-setup")
        val monitorRepository = getMonitorRepository()

        // All the monitors in there should be added to the DB
        val monitorsInDb = monitorRepository.fetchAll() shouldHaveSize 3
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe false
        getAppConfig().isPushMonitorExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.heartbeatInterval shouldBe 120
            firstMonitor.gracePeriod shouldBe 10
            firstMonitor.enabled shouldBe false
            firstMonitor.clientSecret shouldEndWith "somesecret1"
            firstMonitor.integrations.shouldBeEmpty()
            firstMonitor.failureCountThreshold shouldBe 3
        }

        monitorsInDb.forOne { secondMonitor ->
            secondMonitor.name shouldBe "test2"
            secondMonitor.heartbeatInterval shouldBe 60
            secondMonitor.gracePeriod shouldBe 0
            secondMonitor.enabled shouldBe PushMonitorDefaults.MONITOR_ENABLED
            secondMonitor.clientSecret shouldEndWith "somesecret2"
            secondMonitor.integrations shouldBe arrayOf(
                IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")
            )
        }

        monitorsInDb.forOne { thirdMonitor ->
            thirdMonitor.name shouldBe "test3"
            thirdMonitor.heartbeatInterval shouldBe 120
            thirdMonitor.gracePeriod shouldBe 0
            thirdMonitor.enabled shouldBe true
            thirdMonitor.clientSecret shouldEndWith "somesecret3"
            thirdMonitor.integrations.shouldBeEmpty()
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

        appContext = testAppContext("yaml-push-monitors-changed", "full-integrations-setup")
        val monitorRepository = getMonitorRepository()

        // All the monitors in there should be added to the DB
        val monitorsInDb = monitorRepository.fetchAll() shouldHaveSize 3
        // The app config should be set to disable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe false
        getAppConfig().isPushMonitorExternalWriteDisabled() shouldBe true

        monitorsInDb.forOne { firstMonitor ->
            firstMonitor.name shouldBe "test1"
            firstMonitor.heartbeatInterval shouldBe 125
            firstMonitor.gracePeriod shouldBe 0
            firstMonitor.enabled shouldBe true
            firstMonitor.clientSecret shouldEndWith "somesecretU"
            firstMonitor.failureCountThreshold shouldBe 2
            firstMonitor.integrations shouldBe arrayOf(
                IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")
            )
            firstMonitor.updatedAt.shouldNotBeNull() shouldBeAfter firstMonitor.createdAt

            monitorsAfterTheFirstStep.single { it.name == firstMonitor.name }.id shouldBe firstMonitor.id
        }

        monitorsInDb.forOne { unchangedMonitor ->
            unchangedMonitor.name shouldBe "test2"
            unchangedMonitor.heartbeatInterval shouldBe 60
            unchangedMonitor.gracePeriod shouldBe 0
            unchangedMonitor.enabled shouldBe PushMonitorDefaults.MONITOR_ENABLED
            unchangedMonitor.clientSecret shouldEndWith "somesecret2"
            unchangedMonitor.integrations shouldBe arrayOf(
                IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")
            )
            monitorsAfterTheFirstStep.single { it.name == unchangedMonitor.name }.id shouldBe unchangedMonitor.id
        }

        monitorsInDb.forOne { newMonitor ->
            newMonitor.name shouldBe "test4"
            newMonitor.heartbeatInterval shouldBe 15
            newMonitor.gracePeriod shouldBe 0
            newMonitor.enabled shouldBe false
            newMonitor.clientSecret shouldEndWith "somesecret3"
            newMonitor.integrations.shouldBeEmpty()
        }
        // Saving the monitors from the DB to be able to check them later
        monitorsAfterTheSecondStep.addAll(monitorsInDb)
    }

    /**
     * This test simulates a change in the YAML config, where the config property is empty. In this
     * case the app should retain the previously persisted monitors, which is essential
     * because accidentally misconfiguring the YAML config should not cause any data loss
     */
    "3. step: the app is restarted with an empty YAML config for the monitors" {
        appContext = testAppContext("yaml-push-monitors-empty-value")
        val monitorRepository = getMonitorRepository()

        // The app config should be set to enable external writes against the monitors
        getAppConfig().isPushMonitorExternalWriteDisabled() shouldBe false
        // All the previously set up monitors should be still in there
        monitorRepository.fetchAll()
            .shouldHaveSize(3)
            .map { it.name } shouldContainExactlyInAnyOrder monitorsAfterTheSecondStep.map { it.name }

        // Creating a monitor by hand during runtime that should be persisted & scheduled
        createPushMonitor(
            getMonitorRepository(),
            monitorName = "manual_monitor",
            enabled = true,
        )
        monitorRepository.fetchAll() shouldHaveSize 4
    }

    "4. step: the app is restarted with no YAML config for push monitors" {
        appContext = testAppContext("full-integrations-setup")
        val monitorRepository = getMonitorRepository()

        // The app config should be set to enable external writes against the monitors
        getAppConfig().isPushMonitorExternalWriteDisabled() shouldBe false
        // All the previously set up monitors should be still in there, including the manually created one
        val monitorsInDb = monitorRepository.fetchAll().map { it.name }
        monitorsInDb
            .shouldHaveSize(4)
            .shouldContainExactlyInAnyOrder(monitorsAfterTheSecondStep.map { it.name } + "manual_monitor")
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
    "6. step: the app started with an empty array as the YAML config for the monitors" {
        appContext = testAppContext("yaml-push-monitors-empty-array")
        val monitorRepository = getMonitorRepository()

        // The app config should be set to disable external writes against the monitors
        getAppConfig().isHttpMonitorExternalWriteDisabled() shouldBe false
        getAppConfig().isPushMonitorExternalWriteDisabled() shouldBe true
        // All the monitors should be deleted
        monitorRepository.fetchAll().shouldBeEmpty()
    }

    /**
     * This test simulates a case where the YAML config is used, but one of the integrations is not present in the
     * integrations' config. In this case the app should throw an exception, and should not start up.
     */
    "7. step: the app started with some monitors in the YAML, but there is a non-existing integration on one of them" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("yaml-push-monitors-missing-integration", "full-integrations-setup")
        }

        ex.message shouldContain "Non-existing integration ID found: slack:non-existing."
    }
})
