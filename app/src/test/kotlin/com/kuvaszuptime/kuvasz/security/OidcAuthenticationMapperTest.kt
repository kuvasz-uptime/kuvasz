package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.config.OidcConfig
import com.kuvaszuptime.kuvasz.security.oidc.OIDC_PROVIDER_NAME
import com.kuvaszuptime.kuvasz.security.oidc.OidcAuthenticationMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdClaims
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactive.awaitFirst

class OidcAuthenticationMapperTest : BehaviorSpec({

    val tokenResponse = mockk<OpenIdTokenResponse>()

    fun claimsOf(subject: String = "sub-123", email: String? = null, emailVerified: Boolean? = null) =
        mockk<OpenIdClaims> {
            every { this@mockk.subject } returns subject
            every { this@mockk.email } returns email
            every { isEmailVerified } returns emailVerified
        }

    fun configOf(allowedEmails: List<String> = emptyList(), requireVerifiedEmail: Boolean = true) =
        OidcConfig().apply {
            this.allowedEmails = allowedEmails
            this.requireVerifiedEmail = requireVerifiedEmail
        }

    given("the OidcAuthenticationMapper without an email allowlist") {

        val mapper = OidcAuthenticationMapper(configOf())

        `when`("the OIDC claims contain a subject") {
            val claims = claimsOf(subject = "user@example.com")

            then("it returns a successful authentication with WEB and API roles and the subject as principal") {
                val response =
                    mapper.createAuthenticationResponse(OIDC_PROVIDER_NAME, tokenResponse, claims, null).awaitFirst()

                response.isAuthenticated shouldBe true
                response.authentication.get().name shouldBe "user@example.com"
                response.authentication.get().roles shouldContainExactlyInAnyOrder listOf(
                    Role.WEB.alias,
                    Role.API.alias,
                )
            }
        }

        `when`("the user has no email claim at all") {
            val claims = claimsOf(email = null)

            then("it still authenticates the user, as the allowlist is disabled") {
                val response =
                    mapper.createAuthenticationResponse(OIDC_PROVIDER_NAME, tokenResponse, claims, null).awaitFirst()

                response.isAuthenticated shouldBe true
            }
        }
    }

    given("the OidcAuthenticationMapper with an email allowlist (verified required by default)") {

        val mapper = OidcAuthenticationMapper(configOf(allowedEmails = listOf("Allowed@Example.com", " spaced@x.io ")))

        `when`("the verified email is on the allowlist (case- and whitespace-insensitive)") {
            val claims = claimsOf(email = "allowed@example.com", emailVerified = true)

            then("it authenticates the user") {
                val response =
                    mapper.createAuthenticationResponse(OIDC_PROVIDER_NAME, tokenResponse, claims, null).awaitFirst()

                response.isAuthenticated shouldBe true
                response.authentication.get().roles shouldContainExactlyInAnyOrder listOf(
                    Role.WEB.alias,
                    Role.API.alias,
                )
            }
        }

        `when`("a whitespace-padded allowlist entry matches a verified email") {
            val claims = claimsOf(email = "spaced@x.io", emailVerified = true)

            then("it authenticates the user") {
                val response =
                    mapper.createAuthenticationResponse(OIDC_PROVIDER_NAME, tokenResponse, claims, null).awaitFirst()

                response.isAuthenticated shouldBe true
            }
        }

        `when`("the email is allow-listed but not verified") {
            val claims = claimsOf(email = "allowed@example.com", emailVerified = false)

            then("it rejects the login") {
                val response =
                    mapper.createAuthenticationResponse(OIDC_PROVIDER_NAME, tokenResponse, claims, null).awaitFirst()

                response.isAuthenticated shouldBe false
            }
        }

        `when`("the verified email is not on the allowlist") {
            val claims = claimsOf(email = "intruder@example.com", emailVerified = true)

            then("it rejects the login") {
                val response =
                    mapper.createAuthenticationResponse(OIDC_PROVIDER_NAME, tokenResponse, claims, null).awaitFirst()

                response.isAuthenticated shouldBe false
            }
        }

        `when`("there is no email claim at all") {
            val claims = claimsOf(email = null)

            then("it rejects the login (fail closed)") {
                val response =
                    mapper.createAuthenticationResponse(OIDC_PROVIDER_NAME, tokenResponse, claims, null).awaitFirst()

                response.isAuthenticated shouldBe false
            }
        }
    }

    given("the OidcAuthenticationMapper with an email allowlist but verification turned off") {

        val mapper = OidcAuthenticationMapper(
            configOf(allowedEmails = listOf("allowed@example.com"), requireVerifiedEmail = false),
        )

        `when`("the email is on the allowlist but not verified") {
            val claims = claimsOf(email = "allowed@example.com", emailVerified = false)

            then("it authenticates the user, as verification is not required") {
                val response =
                    mapper.createAuthenticationResponse(OIDC_PROVIDER_NAME, tokenResponse, claims, null).awaitFirst()

                response.isAuthenticated shouldBe true
            }
        }
    }
})
