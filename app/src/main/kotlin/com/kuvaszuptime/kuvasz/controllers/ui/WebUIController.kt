package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.repositories.SettingsRepository
import com.kuvaszuptime.kuvasz.security.ui.UnauthenticatedOnly
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.proxy.ProxyRegistry
import com.kuvaszuptime.kuvasz.services.ui.DashboardDataProvider
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
import com.kuvaszuptime.kuvasz.ui.fragments.layout.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.swagger.v3.oas.annotations.Hidden
import java.time.Duration

@Controller("/")
@Hidden
class WebUIController(
    private val appGlobals: AppGlobals,
    private val settingsRepository: SettingsRepository,
    private val integrationsRepository: IntegrationRepository,
    private val incidentRepository: IncidentRepository,
    private val dashboardDataProvider: DashboardDataProvider,
    private val dockerHostRegistry: DockerHostRegistry?,
    private val dockerApiClient: DockerApiClient?,
    private val proxyRegistry: ProxyRegistry,
) {

    companion object {
        const val DASHBOARD_PATH = "/"
        const val DASHBOARD_FRAGMENT_PATH = "/fragments/dashboard"
        const val LOGIN_PATH = "/login"
    }

    @Get(DASHBOARD_PATH)
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun dashboard(@QueryValue period: String?) = renderDashboard(appGlobals, period.toDashboardPeriod())

    @Get(DASHBOARD_FRAGMENT_PATH)
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun dashboardOverview(@QueryValue period: String?) =
        renderDashboardOverview(dashboardDataProvider.getOverview(period.toDashboardPeriod()))

    @Get(CONNECTIVITY_BADGE_FRAGMENT_PATH)
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun connectivityBadge() = renderConnectivityBadgeFragment(appGlobals.connectivityStatus())

    @Get(LOGIN_PATH)
    @UnauthenticatedOnly
    @Produces(MediaType.TEXT_HTML)
    fun login(@QueryValue error: Boolean?): String = renderLoginPage(
        appGlobals,
        loginErrorMessage = if (error == true) Messages.invalidCredentials() else null,
    )

    @Get("/settings")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun settings() = renderSettings(
        globals = appGlobals,
        settings = settingsRepository.getSettings(),
        dockerHosts = dockerHostRegistry?.getHostDtos(dockerApiClient).orEmpty(),
        proxies = proxyRegistry.getProxyDtos(),
    )

    @Get("/integrations")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun integrations() = renderIntegrations(
        globals = appGlobals,
        integrations = integrationsRepository.getConfiguredIntegrationDtos().sortedBy { it.name.lowercase() },
        settings = settingsRepository.getSettings(),
    )

    @Get("/incidents")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun incidents(@QueryValue period: Duration?): String {
        val effectivePeriod = period ?: Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS)
        return renderIncidentsPage(
            globals = appGlobals,
            period = effectivePeriod,
            incidents = incidentRepository.getIncidents(
                monitorId = null,
                period = effectivePeriod,
                includeResolved = true,
            )
        )
    }
}

// Only the periods of the selector are accepted, as the timeline of the dashboard is sliced to fit them
private fun String?.toDashboardPeriod(): Duration =
    this?.let { runCatching { Duration.parse(it) }.getOrNull() }
        ?.takeIf { it in UIDefaults.PERIOD_SELECTOR_OPTIONS }
        ?: Duration.ofDays(UIDefaults.DASHBOARD_MONITORING_STATS_PERIOD_DAYS)
