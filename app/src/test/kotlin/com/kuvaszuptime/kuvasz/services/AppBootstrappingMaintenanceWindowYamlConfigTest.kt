package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
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
 * The steps are dependent and the DB is only cleaned up after the whole class, so take care when changing them.
 */
class AppBootstrappingMaintenanceWindowYamlConfigTest : StringSpec({

    var appContext: ApplicationContext? = null

    afterTest {
        appContext?.stop()
        appContext = null
    }

    afterSpec {
        val ephemeralAppContext = testAppContext()
        ephemeralAppContext.getBean<DSLContext>().resetDatabase()
        ephemeralAppContext.stop()
    }

    fun getRepo() = appContext?.getBean<MaintenanceWindowRepository>().shouldNotBeNull()
    fun getAppConfig() = appContext?.getBean<AppConfig>().shouldNotBeNull()

    "1. step: a valid YAML config imports the windows and disables external writes" {
        appContext = testAppContext("yaml-monitors", "full-integrations-setup", "maintenance-windows")

        val windows = getRepo().fetchAll() shouldHaveSize 2
        getAppConfig().isMaintenanceWindowExternalWriteDisabled() shouldBe true

        windows.forOne { nightly ->
            nightly.name shouldBe "Nightly maintenance"
            nightly.cron shouldBe "0 2 * * *"
            nightly.duration shouldBe "PT1H"
            nightly.showOnStatusPages shouldBe true
            nightly.monitors shouldContainExactly arrayOf(MonitorID(MonitorType.HTTP_SSL, "test1"))
        }
        windows.forOne { oneOff ->
            oneOff.name shouldBe "One-off maintenance"
            oneOff.global shouldBe true
            oneOff.start.shouldNotBeNull()
        }
    }

    "2. step: a changed YAML config upserts the existing windows and prunes the removed ones" {
        delay(1000.milliseconds) // make sure updatedAt differs from createdAt

        appContext = testAppContext("yaml-monitors", "full-integrations-setup", "maintenance-windows-changed")

        val windows = getRepo().fetchAll() shouldHaveSize 2
        getAppConfig().isMaintenanceWindowExternalWriteDisabled() shouldBe true

        windows.forOne { updated ->
            updated.name shouldBe "Nightly maintenance"
            updated.cron shouldBe "0 3 * * *"
            updated.duration shouldBe "PT30M"
            updated.showOnStatusPages shouldBe false
            updated.monitors shouldContainExactlyInAnyOrder arrayOf(
                MonitorID(MonitorType.HTTP_SSL, "test1"),
                MonitorID(MonitorType.HTTP_SSL, "test3"),
            )
            updated.updatedAt shouldBeAfter updated.createdAt
        }
        windows.forOne { new ->
            new.name shouldBe "Brand new window"
            new.enabled shouldBe false
            new.createdAt shouldBe new.updatedAt
        }
    }

    "3. step: an empty array deletes all windows and keeps external writes disabled" {
        appContext = testAppContext("yaml-monitors", "full-integrations-setup", "maintenance-windows-empty-array")

        getAppConfig().isMaintenanceWindowExternalWriteDisabled() shouldBe true
        getRepo().fetchAll().shouldBeEmpty()
    }

    "4. step: a non-existing monitor reference is ignored without failing startup" {
        shouldNotThrowAny {
            appContext = testAppContext(
                "yaml-monitors",
                "full-integrations-setup",
                "maintenance-windows-missing-monitor",
            )
        }

        getRepo().fetchAll().forOne { window ->
            window.name shouldBe "Window with a missing monitor"
            // The non-existing monitor was dropped, only the valid one remains
            window.monitors shouldContainExactly arrayOf(MonitorID(MonitorType.HTTP_SSL, "test1"))
        }
    }

    "5. step: non-configured or malformed integration references are ignored without failing startup" {
        shouldNotThrowAny {
            appContext = testAppContext(
                "yaml-monitors",
                "full-integrations-setup",
                "maintenance-windows-missing-integration",
            )
        }

        getRepo().fetchAll().forOne { window ->
            window.name shouldBe "Window with bad integrations"
            // The non-configured and malformed integrations were dropped, only the configured one remains
            window.integrations shouldContainExactly arrayOf(
                IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
            )
        }
    }

    "6. step: no YAML config re-enables external writes" {
        appContext = testAppContext("yaml-monitors", "full-integrations-setup")

        getAppConfig().isMaintenanceWindowExternalWriteDisabled() shouldBe false
    }
})
