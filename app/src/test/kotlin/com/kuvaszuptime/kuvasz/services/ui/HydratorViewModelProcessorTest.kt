package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.ui.ViewParams
import com.kuvaszuptime.kuvasz.models.ui.emptyViewParams
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.security.utils.SecurityService
import io.micronaut.views.ModelAndView
import io.mockk.every
import io.mockk.mockk

class HydratorViewModelProcessorTest : BehaviorSpec({

    given("the HydratorViewModelProcessor") {

        `when`("when SecurityService is not available - (a.k.a. authentication is disabled)") {

            val processor = HydratorViewModelProcessor(null, AppConfig())
            val viewParams = emptyViewParams()

            processor.process(mockk(), ModelAndView<ViewParams>("irrelevant", viewParams))

            then("it should return the correctly hydrated view model") {
                viewParams["appVersion"] shouldBe BuildConfig.APP_VERSION
                viewParams["isAuthEnabled"] shouldBe false
                viewParams["isAuthenticated"] shouldBe true
                viewParams["isReadOnlyMode"] shouldBe false
            }
        }

        `when`("when the request is authenticated") {
            val mockSecurity = mockk<SecurityService> {
                every { isAuthenticated } returns true
            }
            val processor = HydratorViewModelProcessor(mockSecurity, AppConfig())
            val viewParams = emptyViewParams()

            processor.process(mockk(), ModelAndView<ViewParams>("irrelevant", viewParams))

            then("it should return the correctly hydrated view model") {
                viewParams["appVersion"] shouldBe BuildConfig.APP_VERSION
                viewParams["isAuthEnabled"] shouldBe true
                viewParams["isAuthenticated"] shouldBe true
                viewParams["isReadOnlyMode"] shouldBe false
            }
        }

        `when`("when the request is not authenticated") {
            val mockSecurity = mockk<SecurityService> {
                every { isAuthenticated } returns false
            }
            val processor = HydratorViewModelProcessor(mockSecurity, AppConfig())
            val viewParams = emptyViewParams()

            processor.process(mockk(), ModelAndView<ViewParams>("irrelevant", viewParams))

            then("it should return the correctly hydrated view model") {
                viewParams["appVersion"] shouldBe BuildConfig.APP_VERSION
                viewParams["isAuthEnabled"] shouldBe true
                viewParams["isAuthenticated"] shouldBe false
                viewParams["isReadOnlyMode"] shouldBe false
            }
        }

        `when`("when the app is in read-only mode") {
            val appConfig = AppConfig()
            appConfig.disableExternalWrite()
            val processor = HydratorViewModelProcessor(null, appConfig)
            val viewParams = emptyViewParams()

            processor.process(mockk(), ModelAndView<ViewParams>("irrelevant", viewParams))

            then("it should return the correctly hydrated view model") {
                viewParams["isReadOnlyMode"] shouldBe true
            }
        }
    }
})
