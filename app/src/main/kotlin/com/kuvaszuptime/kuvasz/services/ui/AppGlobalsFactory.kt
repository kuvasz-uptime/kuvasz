package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.DefaultStatusPageConfig
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.models.handlers.type
import com.kuvaszuptime.kuvasz.services.VersionChecker
import com.kuvaszuptime.kuvasz.services.check.http.MonitorActions
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.security.utils.SecurityService
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
        monitorActions: MonitorActions,
    ) = AppGlobals(
        editabilityState = AppGlobals.EditabilityState(
            areHttpMonitorsReadOnly = { appConfig.isHttpMonitorExternalWriteDisabled() },
            areStatusPagesReadOnly = { appConfig.isStatusPageExternalWriteDisabled() },
        ),
        isAuthenticated = { securityService?.isAuthenticated ?: true },
        isAuthEnabled = securityService != null,
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
            monitorActions.getConfiguredMonitors(sortedBy = HTTP_MONITOR.NAME.asc())
        },
    )
}
