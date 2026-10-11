package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.incident.IncidentActions
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.ui.pages.incident.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.swagger.v3.oas.annotations.Hidden
import java.time.Duration

@Controller("/incidents")
@Hidden
class WebUIIncidentController(
    private val appGlobals: AppGlobals,
    private val incidentRepository: IncidentRepository,
    private val incidentActions: IncidentActions,
) {

    @Get("/")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun incidents(@QueryValue period: Duration?, @QueryValue type: IncidentType?): String =
        renderIncidentsPage(globals = appGlobals, period = period.orDefaultIncidentsPeriod(), selectedType = type)

    @Get("/fragments/list")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun incidentList(@QueryValue period: Duration?, @QueryValue type: IncidentType?): String =
        renderIncidentList(
            incidentRepository.getIncidents(
                monitorId = null,
                period = period.orDefaultIncidentsPeriod(),
                includeResolved = true,
                types = type?.let { setOf(it) },
            )
        )

    @Get("/{incidentType}/{eventId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun incidentDetails(@PathVariable incidentType: IncidentType, @PathVariable eventId: Long): String =
        renderIncidentDetailsPage(
            globals = appGlobals,
            details = incidentActions.getIncidentDetails(incidentType, eventId),
        )

    @Get("/fragments/details/{incidentType}/{eventId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun incidentDetailsFragment(
        @PathVariable incidentType: IncidentType,
        @PathVariable eventId: Long,
    ): HttpResponse<String> {
        val details = incidentActions.getIncidentDetails(incidentType, eventId)
        val response = HttpResponse.ok(renderIncidentDetailsFragment(appGlobals, details))

        // A resolved incident never changes again, so htmx is told to stop polling, after swapping in this response
        return if (details.incident.status == IncidentStatus.ONGOING) {
            response
        } else {
            response.status(HTMX_STOP_POLLING_STATUS)
        }
    }

    private fun Duration?.orDefaultIncidentsPeriod(): Duration =
        this ?: Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS)

    companion object {
        private const val HTMX_STOP_POLLING_STATUS = 286
    }
}
