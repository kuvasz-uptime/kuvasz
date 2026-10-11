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
import io.micronaut.http.server.exceptions.NotFoundException
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
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun incidents(@QueryValue period: Duration?, @QueryValue type: String?): String {
        val effectivePeriod = period ?: Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS)
        // An unknown type is ignored, like an absent one, so a stale link still shows something
        val selectedType = type?.let { IncidentType.fromNameOrNull(it) }

        return renderIncidentsPage(
            globals = appGlobals,
            period = effectivePeriod,
            selectedType = selectedType,
            incidents = incidentRepository.getIncidents(
                monitorId = null,
                period = effectivePeriod,
                includeResolved = true,
                types = selectedType?.let { setOf(it) },
            )
        )
    }

    @Get("/{incidentType}/{eventId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun incidentDetails(@PathVariable incidentType: String, @PathVariable eventId: Long): String =
        renderIncidentDetailsPage(
            globals = appGlobals,
            details = incidentActions.getIncidentDetails(incidentType.toIncidentType(), eventId),
        )

    @Get("/fragments/details/{incidentType}/{eventId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun incidentDetailsFragment(
        @PathVariable incidentType: String,
        @PathVariable eventId: Long,
    ): HttpResponse<String> {
        val details = incidentActions.getIncidentDetails(incidentType.toIncidentType(), eventId)
        val response = HttpResponse.ok(renderIncidentDetailsFragment(appGlobals, details))

        // A resolved incident never changes again, so htmx is told to stop polling, after swapping in this response
        return if (details.incident.status == IncidentStatus.ONGOING) {
            response
        } else {
            response.status(HTMX_STOP_POLLING_STATUS)
        }
    }

    private fun String.toIncidentType(): IncidentType = IncidentType.fromNameOrNull(this) ?: throw NotFoundException()

    companion object {
        private const val HTMX_STOP_POLLING_STATUS = 286
    }
}
