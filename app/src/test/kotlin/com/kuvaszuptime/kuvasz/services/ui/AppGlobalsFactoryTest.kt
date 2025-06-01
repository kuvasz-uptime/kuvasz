package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.security.utils.SecurityService
import io.mockk.every
import io.mockk.mockk

class AppGlobalsFactoryTest : BehaviorSpec({

    given("the AppGlobalsFactory") {

        `when`("when SecurityService is not available - (a.k.a. authentication is disabled)") {

            val globals = AppGlobalsFactory().appGlobals(null, AppConfig())

            then("it should return the correctly hydrated view model") {
                globals.appVersion shouldBe BuildConfig.APP_VERSION
                globals.isAuthEnabled shouldBe false
                globals.isAuthenticated() shouldBe true
                globals.isReadOnlyMode shouldBe false
            }
        }

        `when`("when the request is authenticated") {
            val mockSecurity = mockk<SecurityService> {
                every { isAuthenticated } returns true
            }
            val globals = AppGlobalsFactory().appGlobals(mockSecurity, AppConfig())

            then("it should return the correctly hydrated view model") {
                globals.appVersion shouldBe BuildConfig.APP_VERSION
                globals.isAuthEnabled shouldBe true
                globals.isAuthenticated() shouldBe true
                globals.isReadOnlyMode shouldBe false
            }
        }

        `when`("when the request is not authenticated") {
            val mockSecurity = mockk<SecurityService> {
                every { isAuthenticated } returns false
            }
            val globals = AppGlobalsFactory().appGlobals(mockSecurity, AppConfig())

            then("it should return the correctly hydrated view model") {
                globals.appVersion shouldBe BuildConfig.APP_VERSION
                globals.isAuthEnabled shouldBe true
                globals.isAuthenticated() shouldBe false
                globals.isReadOnlyMode shouldBe false
            }
        }

        `when`("when the app is in read-only mode") {
            val appConfig = AppConfig()
            appConfig.disableExternalWrite()
            val globals = AppGlobalsFactory().appGlobals(null, appConfig)

            then("it should return the correctly hydrated view model") {
                globals.isReadOnlyMode shouldBe true
            }
        }
    }
})
