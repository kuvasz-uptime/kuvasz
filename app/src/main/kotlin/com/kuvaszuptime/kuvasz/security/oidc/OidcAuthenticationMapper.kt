package com.kuvaszuptime.kuvasz.security.oidc

import com.kuvaszuptime.kuvasz.config.OidcConfig
import com.kuvaszuptime.kuvasz.security.Role
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.oauth2.endpoint.authorization.state.State
import io.micronaut.security.oauth2.endpoint.token.response.OauthAuthenticationMapper
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
class OidcAuthenticationMapper(
    private val oidcConfig: OidcConfig,
) : OpenIdAuthenticationMapper {

    companion object {
        private val logger = loggerFor<OidcAuthenticationMapper>()
    }

    override fun createAuthenticationResponse(
        providerName: String,
        tokenResponse: OpenIdTokenResponse,
        openIdClaims: OpenIdClaims,
        state: State?,
    ): Publisher<AuthenticationResponse> {
        val response = if (oidcConfig.isEmailAllowed(openIdClaims.email, openIdClaims.isEmailVerified)) {
            AuthenticationResponse.success(
                openIdClaims.subject,
                listOf(Role.WEB.alias, Role.API.alias),
                // The end-session (/oauth/logout) finds the provider of the user by it. Without it, Micronaut falls
                // back to matching the `iss` claim of our own JWT against the issuer of the provider, which never
                // matches since micronaut-security 5.4.2, so the route answers 404
                mapOf(OauthAuthenticationMapper.PROVIDER_KEY to providerName),
            )
        } else {
            logger.warn("Rejected OIDC login for subject [${openIdClaims.subject}]: not in the configured allowlist")
            AuthenticationResponse.failure("User is not allowed to sign in")
        }

        return Publishers.just(response)
    }
}
