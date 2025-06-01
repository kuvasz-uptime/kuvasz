package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.i18n.Messages
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = "test-api-key-fjklafdjkldsfjdsklfds")
@Property(name = "admin-auth.username", value = "test-username")
@Property(name = "admin-auth.password", value = "test-password-fdsjkfldsjfkdls")
@MicronautTest(startApplication = false)
class WebUILoginControllerTest(controller: WebUIController) : DatabaseBehaviorSpec({

    given("the WebUIController's /login endpoint") {

        `when`("it is called without ?error") {

            val html = controller.login(null)

            then("it should not add the error message to the model") {
                html shouldNotContain Messages.invalidCredentials()
            }
        }

        `when`("it is called with ?error=false") {

            val html = controller.login(false)

            then("it should not add the error message to the model") {
                html shouldNotContain Messages.invalidCredentials()
            }
        }

        `when`("it is called with ?error=true") {

            val html = controller.login(true)

            then("it should add the error message to the model") {
                html shouldContain Messages.invalidCredentials()
            }
        }
    }
})
