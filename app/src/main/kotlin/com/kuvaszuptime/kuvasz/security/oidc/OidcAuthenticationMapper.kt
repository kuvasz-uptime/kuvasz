package com.kuvaszuptime.kuvasz.security.oidc

import com.kuvaszuptime.kuvasz.security.Role
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.oauth2.endpoint.authorization.state.State
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdAuthenticationMapper
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdClaims
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

const val OIDC_PROVIDER_NAME = "oidc"

@Singleton
@Named(OIDC_PROVIDER_NAME)
@Requires(property = "micronaut.security.oauth2.clients.oidc.enabled", value = "true")
class OidcAuthenticationMapper : OpenIdAuthenticationMapper {

    override fun createAuthenticationResponse(
        providerName: String,
        tokenResponse: OpenIdTokenResponse,
        openIdClaims: OpenIdClaims,
        state: State?,
    ): Publisher<AuthenticationResponse> =
        Publishers.just(
            AuthenticationResponse.success(
                openIdClaims.subject,
                listOf(Role.WEB.alias, Role.API.alias),
            )
        )
}
