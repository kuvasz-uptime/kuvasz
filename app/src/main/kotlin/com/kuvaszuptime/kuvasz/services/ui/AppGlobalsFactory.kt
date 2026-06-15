package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.ApiKeyConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.DefaultStatusPageConfig
import com.kuvaszuptime.kuvasz.models.handlers.type
import com.kuvaszuptime.kuvasz.security.oidc.OIDC_PROVIDER_NAME
import com.kuvaszuptime.kuvasz.services.VersionChecker
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.monitor.SharedMonitorActions
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.security.oauth2.client.OpenIdClient
import io.micronaut.security.utils.SecurityService
import jakarta.inject.Named
import java.util.Locale

@Factory
class AppGlobalsFactory {

    @Context
    fun appGlobals(
        securityService: SecurityService?,
        appConfig: AppConfig,
        integrationRepository: IntegrationRepository,
        versionChecker: VersionChecker,
        defaultStatusPageConfig: DefaultStatusPageConfig,
        monitorActions: SharedMonitorActions,
        // Relying on the conditionally provisioned Micronaut OIDC client bean:
        // it is only present when OIDC is actually enabled, and its supportsEndSession() flag reflects whether
        // Micronaut could resolve an end-session endpoint (i.e. whether the /oauth/logout route really exists),
        // which can be false even with end-session enabled if the provider doesn't advertise that endpoint.
        @Named(OIDC_PROVIDER_NAME) oidcClient: OpenIdClient?,
        // Only present when security is enabled; API key auth is considered enabled only if a key is configured
        apiKeyConfig: ApiKeyConfig?,
    ) = AppGlobals(
        editabilityState = AppGlobals.EditabilityState(
            areHttpMonitorsReadOnly = { appConfig.isHttpMonitorExternalWriteDisabled() },
            arePushMonitorsReadOnly = { appConfig.isPushMonitorExternalWriteDisabled() },
            areIcmpMonitorsReadOnly = { appConfig.isIcmpMonitorExternalWriteDisabled() },
            areStatusPagesReadOnly = { appConfig.isStatusPageExternalWriteDisabled() },
        ),
        isAuthenticated = { securityService?.isAuthenticated ?: true },
        isAuthEnabled = securityService != null,
        isOidcEnabled = oidcClient != null,
        isOidcLogoutEnabled = oidcClient?.supportsEndSession() == true,
        isApiKeyAuthEnabled = !apiKeyConfig?.apiKey.isNullOrBlank(),
        appVersion = BuildConfig.APP_VERSION,
        locale = Locale.of(appConfig.language),
        configuredIntegrations = integrationRepository.configuredIntegrations,
        enabledIntegrations = integrationRepository.enabledIntegrations,
        configuredIntegrationsByType = integrationRepository
            .configuredIntegrations
            .values
            .groupBy { it.type }
            .mapValues { (_, configs) -> configs.toSet() }
            .toMap(),
        versionInfo = { versionChecker.getVersionInfo() },
        defaultStatusPageSettings = AppGlobals.DefaultStatusPageSettings(
            title = defaultStatusPageConfig.title,
            public = defaultStatusPageConfig.public,
        ),
        configuredMonitors = {
            monitorActions.getConfiguredMonitors().sortedBy { it.name }
        },
    )
}
