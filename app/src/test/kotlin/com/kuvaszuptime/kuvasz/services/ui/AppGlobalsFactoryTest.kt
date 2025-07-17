package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationMap
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.type
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
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

    given("the AppGlobalsFactory") {

        `when`("when SecurityService is not available - (a.k.a. authentication is disabled)") {

            val globals = AppGlobalsFactory().appGlobals(null, AppConfig(), emptyIntegrationRepository)

            then("it should return the correctly hydrated view model") {
                globals.appVersion shouldBe BuildConfig.APP_VERSION
                globals.isAuthEnabled shouldBe false
                globals.isAuthenticated() shouldBe true
                globals.isReadOnlyMode() shouldBe false
            }
        }

        `when`("when the request is authenticated") {
            val mockSecurity = mockk<SecurityService> {
                every { isAuthenticated } returns true
            }
            val globals = AppGlobalsFactory().appGlobals(mockSecurity, AppConfig(), emptyIntegrationRepository)

            then("it should return the correctly hydrated view model") {
                globals.appVersion shouldBe BuildConfig.APP_VERSION
                globals.isAuthEnabled shouldBe true
                globals.isAuthenticated() shouldBe true
                globals.isReadOnlyMode() shouldBe false
            }
        }

        `when`("when the request is not authenticated") {
            val mockSecurity = mockk<SecurityService> {
                every { isAuthenticated } returns false
            }
            val globals = AppGlobalsFactory().appGlobals(mockSecurity, AppConfig(), emptyIntegrationRepository)

            then("it should return the correctly hydrated view model") {
                globals.appVersion shouldBe BuildConfig.APP_VERSION
                globals.isAuthEnabled shouldBe true
                globals.isAuthenticated() shouldBe false
                globals.isReadOnlyMode() shouldBe false
            }
        }

        `when`("when the app is in read-only mode") {
            val appConfig = AppConfig()
            appConfig.disableExternalWrite()
            val globals = AppGlobalsFactory().appGlobals(null, appConfig, emptyIntegrationRepository)

            then("it should return the correctly hydrated view model") {
                globals.isReadOnlyMode() shouldBe true
            }
        }

        `when`("when the app is in read-only mode but it's only set later") {
            val appConfig = AppConfig()
            val globals = AppGlobalsFactory().appGlobals(null, appConfig, emptyIntegrationRepository)
            globals.isReadOnlyMode() shouldBe false

            appConfig.disableExternalWrite()
            val globalsAfterUpdate = AppGlobalsFactory().appGlobals(null, appConfig, emptyIntegrationRepository)

            then("it should return the correctly hydrated view model") {
                globalsAfterUpdate.isReadOnlyMode() shouldBe true
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
            val globals = AppGlobalsFactory().appGlobals(null, AppConfig(), mockIntegrationRepository)

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
    }
})
