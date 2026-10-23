package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.incident.IncidentActions
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.ui.pages.incident.*
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.swagger.v3.oas.annotations.Hidden

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
    fun incidents(request: HttpRequest<*>): String =
        renderIncidentsPage(globals = appGlobals, query = incidentListQuery(request.queryParams()))

    @Get("/fragments/list")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun incidentList(request: HttpRequest<*>): HttpResponse<String> {
        val query = incidentListQuery(request.queryParams())
        val incidents = fetchIncidents(query)
        return pagedListFragment(query, incidents) { shownQuery -> renderIncidentList(incidents, shownQuery) }
    }

    /**
     * The incidents of [incidentType] of a monitor on its details page. Their page is not put into the URL, since it's
     * the one of the details page.
     */
    @Get("/fragments/monitor/{incidentType}/{monitorId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun monitorIncidentList(
        @PathVariable incidentType: IncidentType,
        @PathVariable monitorId: Long,
        request: HttpRequest<*>,
    ): String {
        val query = monitorIncidentListQuery(request.queryParams(), incidentType, monitorId)
        val incidents = fetchIncidents(query)
        return renderIncidents(incidents, query.withPage(incidents.page))
    }

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

    private fun fetchIncidents(query: IncidentListQuery): PagedResult<IncidentDto> {
        val filters = query.filters
        return fetchListPage(query.pageRequest) { pageRequest ->
            incidentRepository.getIncidentsPage(
                monitorId = filters.monitorId.value,
                period = filters.period.value,
                includeResolved = filters.includeResolved.value,
                type = filters.type.value,
                name = filters.name.value,
                sorting = query.sorting,
                pageRequest = pageRequest,
            )
        }
    }

    companion object {
        private const val HTMX_STOP_POLLING_STATUS = 286
    }
}
