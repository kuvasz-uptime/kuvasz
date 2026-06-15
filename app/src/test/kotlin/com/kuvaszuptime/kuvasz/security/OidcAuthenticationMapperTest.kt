package com.kuvaszuptime.kuvasz.security

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

    given("the OidcAuthenticationMapper") {

        val mapper = OidcAuthenticationMapper()
        val tokenResponse = mockk<OpenIdTokenResponse>()

        `when`("the OIDC claims contain a subject") {
            val claims = mockk<OpenIdClaims> {
                every { subject } returns "user@example.com"
            }

            then("it should return a successful authentication with WEB and API roles and the subject as principal") {
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
    }
})
