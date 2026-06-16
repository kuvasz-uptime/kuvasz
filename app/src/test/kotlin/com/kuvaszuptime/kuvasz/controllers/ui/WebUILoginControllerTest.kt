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

    given("the WebUIController's /login endpoint - user/pass auth") {

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

        `when`("it is called without ?error") {

            val html = controller.login(null)

            then("it should render the username/password form, not the OIDC button") {
                html shouldContain "/auth/login"
                html shouldNotContain "/oauth/login/oidc"
            }
        }
    }
})

@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = "test-api-key-fjklafdjkldsfjdsklfds")
@Property(name = "admin-auth.username", value = "test-username")
@Property(name = "admin-auth.password", value = "test-password-fdsjkfldsjfkdls")
@Property(name = "micronaut.security.oauth2.clients.oidc.enabled", value = "true")
@Property(name = "micronaut.security.oauth2.clients.oidc.client-id", value = "dummy-client-id")
@Property(name = "micronaut.security.oauth2.clients.oidc.client-secret", value = "dummy-client-secret")
@Property(name = "micronaut.security.oauth2.clients.oidc.openid.issuer", value = "http://localhost:59999/")
@MicronautTest(startApplication = false)
class WebUILoginControllerOidcTest(controller: WebUIController) : DatabaseBehaviorSpec({

    given("the WebUIController's /login endpoint - OIDC auth") {

        `when`("it is called") {

            val html = controller.login(null)

            then("it should render the OIDC sign-in button") {
                html shouldContain "/oauth/login/oidc"
                html shouldContain Messages.loginWithOidc()
            }

            then("it should not render the username/password form") {
                html shouldNotContain "/auth/login"
            }
        }
    }
})
