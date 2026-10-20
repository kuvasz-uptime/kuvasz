package com.kuvaszuptime.kuvasz.security.oidc

import com.kuvaszuptime.kuvasz.controllers.ui.WebUIController
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.filter.ServerFilterPhase
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.oauth2.client.DefaultOpenIdProviderMetadata
import java.net.URI

private const val OIDC_LOGOUT_PATH = "/oauth/logout"
private const val LOCAL_LOGOUT_PATH = "/auth/logout"

/**
 * Workaround for https://github.com/micronaut-projects/micronaut-security/issues/1797
 *
 * The OIDC provider metadata is fetched eagerly at startup with a blocking HTTP client. If the provider is unreachable
 * at that point, Micronaut retries the fetch lazily on the first OAuth request, but that runs on the Netty event loop,
 * where the blocking client refuses to work, so the login stays broken until a restart. Resolving the metadata bean
 * here on a blocking thread makes the retry succeed (and get cached) as soon as the provider is reachable again.
 * The resolved JWKS URI is also handed over to [OidcLazyJwksSignature], to keep the ID tokens verifiable.
 *
 * While the provider is still unreachable, the user is redirected to the login page with an explanation, or in case of
 * a logout, to the local logout, which signs them out of Kuvasz without the provider's end-session.
 **/
@ServerFilter("/oauth/**")
@Requires(property = "micronaut.security.oauth2.clients.oidc.enabled", value = "true")
class OidcProviderMetadataFilter(
    private val beanContext: BeanContext,
    private val lazyJwksSignature: OidcLazyJwksSignature,
) : Ordered {

    companion object {
        private val logger = loggerFor<OidcProviderMetadataFilter>()
    }

    override fun getOrder(): Int = ServerFilterPhase.SECURITY.before()

    @RequestFilter
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun ensureProviderMetadata(request: HttpRequest<*>): HttpResponse<*>? {
        val metadata = beanContext.findBean(
            DefaultOpenIdProviderMetadata::class.java,
            Qualifiers.byName(OIDC_PROVIDER_NAME),
        )
        if (metadata.isPresent) {
            lazyJwksSignature.activate(metadata.get().jwksUri)
            return null
        }

        logger.warn(
            "The OIDC provider's configuration is not available (yet), it will be fetched again on the next request"
        )
        val fallbackPath = if (request.path == OIDC_LOGOUT_PATH) {
            LOCAL_LOGOUT_PATH
        } else {
            "${WebUIController.LOGIN_PATH}?oidcUnavailable=true"
        }
        return HttpResponse.seeOther<Any>(URI.create(fallbackPath))
    }
}
