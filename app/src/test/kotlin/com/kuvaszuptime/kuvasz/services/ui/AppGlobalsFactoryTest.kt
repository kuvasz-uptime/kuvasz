package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.DefaultStatusPageConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationMap
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.type
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.settings.VersionInfo
import com.kuvaszuptime.kuvasz.services.VersionChecker
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.monitor.SharedMonitorActions
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.security.utils.SecurityService
import io.mockk.every
import io.mockk.mockk

class AppGlobalsFactoryTest : BehaviorSpec({

    val emptyIntegrationRepository = mockk<IntegrationRepository> {
        every { enabledIntegrations } returns emptyMap()
        every { configuredIntegrations } returns emptyMap()
    }
    val mockVersionChecker = mockk<VersionChecker> {
        every { getVersionInfo() } returns VersionInfo(
            installedVersion = BuildConfig.APP_VERSION,
            latestVersion = "1.1.1",
            latestVersionDetails = "https://kuvasz-uptime.dev/changelog/#1.1.1".toUri(),
        )
    }
    val mockDefaultPageSettings = mockk<DefaultStatusPageConfig> {
        every { title } returns "My Status Page"
        every { public } returns true
    }
    val mockkMonitorActions = mockk<SharedMonitorActions> {
        every { getConfiguredMonitors() } returns listOf(
            MonitorID(MonitorType.HTTP_SSL, "something"),
            MonitorID(MonitorType.PUSH, "abcd"),
        )
    }

    given("the AppGlobalsFactory") {

        `when`("when SecurityService is not available - (a.k.a. authentication is disabled)") {

            val globals = AppGlobalsFactory().appGlobals(
                null,
                AppConfig(),
                emptyIntegrationRepository,
                mockVersionChecker,
                mockDefaultPageSettings,
                mockkMonitorActions,
            )

            then("it should return the correctly hydrated view model") {
                globals.appVersion shouldBe BuildConfig.APP_VERSION
                globals.isAuthEnabled shouldBe false
                globals.isAuthenticated() shouldBe true
                globals.editabilityState.areHttpMonitorsReadOnly() shouldBe false
                globals.editabilityState.areStatusPagesReadOnly() shouldBe false
                globals.editabilityState.arePushMonitorsReadOnly() shouldBe false
            }
        }

        `when`("when the request is authenticated") {
            val mockSecurity = mockk<SecurityService> {
                every { isAuthenticated } returns true
            }
            val globals = AppGlobalsFactory().appGlobals(
                mockSecurity,
                AppConfig(),
                emptyIntegrationRepository,
                mockVersionChecker,
                mockDefaultPageSettings,
                mockkMonitorActions,
            )

            then("it should return the correctly hydrated view model") {
                globals.appVersion shouldBe BuildConfig.APP_VERSION
                globals.isAuthEnabled shouldBe true
                globals.isAuthenticated() shouldBe true
                globals.editabilityState.areHttpMonitorsReadOnly() shouldBe false
                globals.editabilityState.areStatusPagesReadOnly() shouldBe false
                globals.editabilityState.arePushMonitorsReadOnly() shouldBe false
            }
        }

        `when`("when the request is not authenticated") {
            val mockSecurity = mockk<SecurityService> {
                every { isAuthenticated } returns false
            }
            val globals = AppGlobalsFactory().appGlobals(
                mockSecurity,
                AppConfig(),
                emptyIntegrationRepository,
                mockVersionChecker,
                mockDefaultPageSettings,
                mockkMonitorActions,
            )

            then("it should return the correctly hydrated view model") {
                globals.appVersion shouldBe BuildConfig.APP_VERSION
                globals.isAuthEnabled shouldBe true
                globals.isAuthenticated() shouldBe false
                globals.editabilityState.areHttpMonitorsReadOnly() shouldBe false
                globals.editabilityState.areStatusPagesReadOnly() shouldBe false
                globals.editabilityState.arePushMonitorsReadOnly() shouldBe false
            }
        }

        `when`("when the app is in read-only mode") {
            val appConfig = AppConfig()
            appConfig.disableHttpMonitorExternalWrite()
            appConfig.disablePushMonitorExternalWrite()
            appConfig.disableStatusPageExternalWrite()
            val globals = AppGlobalsFactory().appGlobals(
                null,
                appConfig,
                emptyIntegrationRepository,
                mockVersionChecker,
                mockDefaultPageSettings,
                mockkMonitorActions,
            )

            then("it should return the correctly hydrated view model") {
                globals.editabilityState.areHttpMonitorsReadOnly() shouldBe true
                globals.editabilityState.areStatusPagesReadOnly() shouldBe true
                globals.editabilityState.arePushMonitorsReadOnly() shouldBe true
            }
        }

        `when`("when the app is in read-only mode but it's only set later") {
            val appConfig = AppConfig()
            val globals = AppGlobalsFactory().appGlobals(
                null,
                appConfig,
                emptyIntegrationRepository,
                mockVersionChecker,
                mockDefaultPageSettings,
                mockkMonitorActions,
            )
            globals.editabilityState.areHttpMonitorsReadOnly() shouldBe false
            globals.editabilityState.areStatusPagesReadOnly() shouldBe false
            globals.editabilityState.arePushMonitorsReadOnly() shouldBe false

            appConfig.disableHttpMonitorExternalWrite()
            appConfig.disableStatusPageExternalWrite()
            appConfig.disablePushMonitorExternalWrite()
            val globalsAfterUpdate = AppGlobalsFactory().appGlobals(
                null,
                appConfig,
                emptyIntegrationRepository,
                mockVersionChecker,
                mockDefaultPageSettings,
                mockkMonitorActions,
            )

            then("it should return the correctly hydrated view model") {
                globalsAfterUpdate.editabilityState.areHttpMonitorsReadOnly() shouldBe true
                globalsAfterUpdate.editabilityState.areStatusPagesReadOnly() shouldBe true
                globals.editabilityState.arePushMonitorsReadOnly() shouldBe true
            }
        }

        `when`("there are configured integrations") {
            val enabledIntegrationsMock: IntegrationMap = mapOf(
                IntegrationID(IntegrationType.EMAIL, "test1") to mockk()
            )
            val configuredIntegrationsMock: IntegrationMap = mapOf(
                IntegrationID(IntegrationType.EMAIL, "test1") to mockk(),
                IntegrationID(IntegrationType.SLACK, "test2") to mockk(),
            )
            val mockIntegrationRepository = mockk<IntegrationRepository> {
                every { enabledIntegrations } returns enabledIntegrationsMock
                every { configuredIntegrations } returns configuredIntegrationsMock
            }
            val globals = AppGlobalsFactory().appGlobals(
                null,
                AppConfig(),
                mockIntegrationRepository,
                mockVersionChecker,
                mockDefaultPageSettings,
                mockkMonitorActions,
            )

            then("it should return the correctly hydrated view model with integrations") {
                globals.configuredIntegrations shouldBe configuredIntegrationsMock
                globals.enabledIntegrations shouldBe enabledIntegrationsMock
                globals.configuredIntegrationsByType shouldBe configuredIntegrationsMock
                    .values
                    .groupBy { it.type }
                    .mapValues { (_, configs) -> configs.toSet() }
                    .toMap()
            }
        }

        `when`("the version checker provides a full version info") {

            then("it should return it") {

                val globals = AppGlobalsFactory().appGlobals(
                    null,
                    AppConfig(),
                    emptyIntegrationRepository,
                    mockVersionChecker,
                    mockDefaultPageSettings,
                    mockkMonitorActions,
                )
                globals.versionInfo() shouldBe VersionInfo(
                    installedVersion = BuildConfig.APP_VERSION,
                    latestVersion = "1.1.1",
                    latestVersionDetails = "https://kuvasz-uptime.dev/changelog/#1.1.1".toUri(),
                )
            }
        }

        `when`("the default status page settings are not the defaults") {
            val globals = AppGlobalsFactory().appGlobals(
                null,
                AppConfig(),
                emptyIntegrationRepository,
                mockVersionChecker,
                mockDefaultPageSettings,
                mockkMonitorActions,
            )

            then("it should return the correct default status page settings") {
                globals.defaultStatusPageSettings.title shouldBe "My Status Page"
                globals.defaultStatusPageSettings.public shouldBe true
            }
        }

        `when`("there are enabled monitors") {
            val globals = AppGlobalsFactory().appGlobals(
                null,
                AppConfig(),
                emptyIntegrationRepository,
                mockVersionChecker,
                mockDefaultPageSettings,
                mockkMonitorActions,
            )

            then("it should return the correct list of enabled monitors") {
                globals.configuredMonitors() shouldBe listOf(
                    MonitorID(MonitorType.PUSH, "abcd"),
                    MonitorID(MonitorType.HTTP_SSL, "something")
                )
            }
        }
    }
})
