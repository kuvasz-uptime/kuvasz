package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import com.kuvaszuptime.kuvasz.config.ApiKeyConfig
import com.kuvaszuptime.kuvasz.config.OidcConfig
import com.kuvaszuptime.kuvasz.security.oidc.OidcAuthenticationMapper
import com.kuvaszuptime.kuvasz.security.ui.WebAuthProvider
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException

class OidcBeanWiringTest : BehaviorSpec({

    val adminAuthProps = mapOf(
        "micronaut.security.enabled" to true,
        "admin-auth.username" to "test-user",
        "admin-auth.password" to "validPassword123",
        "admin-auth.api-key" to "validApiKeyvalidApiKeyvalidApiKeyvalidApiKey",
    )

    val oidcProps = mapOf(
        "micronaut.security.oauth2.clients.oidc.enabled" to true,
        "micronaut.security.oauth2.clients.oidc.client-id" to "dummy-client-id",
        "micronaut.security.oauth2.clients.oidc.client-secret" to "dummy-client-secret",
        "micronaut.security.oauth2.clients.oidc.openid.issuer" to "http://localhost:59999/",
    )

    given("the OIDC bean wiring") {

        `when`("the OIDC client is not enabled (default)") {
            val ctx = shouldNotThrowAny { testAppContext(PropertySource.of("test", adminAuthProps)) }

            then("the WebAuthProvider should be present, handling the username/password login") {
                shouldNotThrowAny { ctx.getBean(WebAuthProvider::class.java) }
            }

            then("the OidcAuthenticationMapper should NOT be present") {
                shouldThrow<NoSuchBeanException> { ctx.getBean(OidcAuthenticationMapper::class.java) }
            }

            then("the OidcConfig should NOT be present") {
                shouldThrow<NoSuchBeanException> { ctx.getBean(OidcConfig::class.java) }
            }

            then("the AdminAuthConfig should be present, holding the username/password credentials") {
                shouldNotThrowAny { ctx.getBean(AdminAuthConfig::class.java) }
            }

            then("the ApiKeyConfig should be present") {
                shouldNotThrowAny { ctx.getBean(ApiKeyConfig::class.java) }
            }
        }

        `when`("the OIDC client is enabled via the built-in config props") {
            val properties = PropertySource.of("test", adminAuthProps + oidcProps)
            val ctx = shouldNotThrowAny { testAppContext(properties) }

            then("the OidcAuthenticationMapper should be present") {
                shouldNotThrowAny { ctx.getBean(OidcAuthenticationMapper::class.java) }
            }

            then("the OidcConfig should be present") {
                shouldNotThrowAny { ctx.getBean(OidcConfig::class.java) }
            }

            then("the WebAuthProvider should NOT be present, disabling the username/password login") {
                shouldThrow<NoSuchBeanException> { ctx.getBean(WebAuthProvider::class.java) }
            }

            then("the AdminAuthConfig should NOT be present, as the username/password login is disabled") {
                shouldThrow<NoSuchBeanException> { ctx.getBean(AdminAuthConfig::class.java) }
            }

            then("the ApiKeyConfig should still be present, as API key auth works regardless of OIDC") {
                shouldNotThrowAny { ctx.getBean(ApiKeyConfig::class.java) }
            }
        }

        `when`("the OIDC client is enabled but no admin username/password is configured") {
            // The admin credentials are not required when OIDC is set up
            val properties = PropertySource.of("test", mapOf("micronaut.security.enabled" to true) + oidcProps)
            val ctx = shouldNotThrowAny { testAppContext(properties) }

            then("the ApplicationContext should still start up successfully") {
                shouldNotThrowAny { ctx.getBean(OidcAuthenticationMapper::class.java) }
            }

            then("the AdminAuthConfig should NOT be present") {
                shouldThrow<NoSuchBeanException> { ctx.getBean(AdminAuthConfig::class.java) }
            }
        }

        `when`("the email allowlist is provided as a comma-separated string (the env-var form)") {
            val properties = PropertySource.of(
                "test",
                adminAuthProps + oidcProps + mapOf(
                    "admin-auth.oidc.allowed-emails" to "alice@acme.com,bob@acme.com",
                    "admin-auth.oidc.require-verified-email" to false,
                ),
            )
            val ctx = shouldNotThrowAny { testAppContext(properties) }

            then("the OidcConfig binds it into a list and exposes the verification flag") {
                val config = ctx.getBean(OidcConfig::class.java)
                config.allowedEmails shouldContainExactlyInAnyOrder listOf("alice@acme.com", "bob@acme.com")
                config.requireVerifiedEmail shouldBe false
                config.isEmailAllowlistEnabled shouldBe true
            }
        }
    }
})
