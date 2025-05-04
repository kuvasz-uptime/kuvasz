package com.kuvaszuptime.kuvasz.controllers.ui

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * Test cases that are missing from here, are covered by the authentication tests.
 * Classic UI tests should be added with a different approach once when the UI is more mature and changes less.
 * Currently, the only endpoint that is "stable" in terms of functionality and features is the /login endpoint.
 */
@MicronautTest(startApplication = false)
class WebUIControllerTest(controller: WebUIController) : BehaviorSpec({

    given("the WebUIController's /login endpoint") {

        `when`("it is called without ?error") {

            val viewParams = controller.login(null)

            then("it should not add the error message to the model") {
                viewParams["loginErrorMessage"].shouldBeNull()
            }
        }

        `when`("it is called with ?error=false") {

            val viewParams = controller.login(false)

            then("it should add the error message to the model") {
                viewParams["loginErrorMessage"].shouldBeNull()
            }
        }

        `when`("it is called with ?error=true") {

            val viewParams = controller.login(true)

            then("it should add the error message to the model") {
                viewParams["loginErrorMessage"] shouldBe "Invalid username or password"
            }
        }
    }
})
