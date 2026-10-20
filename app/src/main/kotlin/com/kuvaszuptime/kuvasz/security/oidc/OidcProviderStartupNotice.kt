package com.kuvaszuptime.kuvasz.security.oidc

import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.security.oauth2.client.DefaultOpenIdProviderMetadata
import jakarta.inject.Singleton

/**
 * Part of the workaround for https://github.com/micronaut-projects/micronaut-security/issues/1797 (see
 * [OidcProviderMetadataFilter])
 *
 * If the OIDC provider is unreachable during the startup, Micronaut logs a few errors that look serious, while the
 * login recovers by itself once the provider is up (unless it's misconfigured). This notice puts them into context. The
 * metadata bean is not looked up to find out whether it's missing, because that would trigger another (failing) fetch.
 **/
@Singleton
@Requires(property = "micronaut.security.oauth2.clients.oidc.enabled", value = "true")
class OidcProviderStartupNotice :
    BeanCreatedEventListener<DefaultOpenIdProviderMetadata>,
    ApplicationEventListener<StartupEvent> {

    companion object {
        private val logger = loggerFor<OidcProviderStartupNotice>()
    }

    private var isMetadataAvailable = false

    override fun onCreated(event: BeanCreatedEvent<DefaultOpenIdProviderMetadata>): DefaultOpenIdProviderMetadata {
        isMetadataAvailable = true
        return event.bean
    }

    override fun onApplicationEvent(event: StartupEvent) {
        if (isMetadataAvailable) return
        logger.warn(
            "The OIDC provider's configuration couldn't be fetched during the startup, so the OIDC login is " +
                "unavailable for now. If the provider is just starting up, the errors logged above by " +
                "DefaultOpenIdProviderMetadataFetcher (and a \"BlockingHttpClient operation on a netty event loop " +
                "thread\" one on the first request) are harmless: the login recovers automatically, without a " +
                "restart, as soon as the provider becomes reachable. If it keeps failing, check the OIDC issuer " +
                "configuration and whether the provider is reachable from Kuvasz."
        )
    }
}
