package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardOverview
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.models.monitorType
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowActions
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class DashboardDataProvider(
    private val statCalculator: StatCalculator,
    private val incidentRepository: IncidentRepository,
    private val maintenanceWindowActions: MaintenanceWindowActions,
) {

    companion object {
        const val RECENT_INCIDENTS_LIMIT = 6
        const val MAINTENANCE_WINDOWS_LIMIT = 5
        // The texts of the maintenance card say "7 days" too
        val MAINTENANCE_LOOKAHEAD: Duration = Duration.ofDays(7)
    }

    /**
     * Collects everything the dashboard shows over the given [period].
     */
    fun getOverview(period: Duration): DashboardOverview {
        // Loaded once, as both the figures and the card of the maintenance windows rely on them
        val maintenanceWindows = maintenanceWindowActions.getMaintenanceWindows()
        val uptimeStats = statCalculator.calculateDashboardUptimeStats(period, maintenanceWindows)

        // The ongoing incidents come first, and the ones that don't fit are counted, so no monitor that is down goes
        // unnoticed. Being down is expected during a maintenance, so those can't push the rest out of the list. The
        // list is only topped up with the resolved ones if there is room left. Just like the figures next to them,
        // they're the uptime incidents only, the certificates have a card anyway.
        val ongoingIncidents = incidentRepository
            .getIncidents(includeResolved = false, includeSslIncidents = false)
            .sortedWith(
                compareBy<IncidentDto> { it.numericMonitorId in uptimeStats.monitorsInMaintenance }
                    .thenByDescending { it.startedAt }
            )
        val listedOngoingIncidents = ongoingIncidents.take(RECENT_INCIDENTS_LIMIT)
        val resolvedIncidentsLimit = RECENT_INCIDENTS_LIMIT - listedOngoingIncidents.size
        val resolvedIncidents = if (resolvedIncidentsLimit > 0) {
            incidentRepository.getLatestResolvedIncidents(
                period,
                limit = resolvedIncidentsLimit,
                includeSslIncidents = false,
            )
        } else {
            emptyList()
        }
        // An incident resolved between the two queries would be listed by both of them, so only its latest state is
        // kept
        val resolvedIncidentKeys = resolvedIncidents.map { it.key }.toSet()
        val activeAndUpcomingWindows = maintenanceWindows.activeAndUpcoming()

        return DashboardOverview(
            period = period,
            uptimeStats = uptimeStats,
            recentIncidents = listedOngoingIncidents.filterNot { it.key in resolvedIncidentKeys } + resolvedIncidents,
            moreOngoingIncidents = ongoingIncidents.size - listedOngoingIncidents.size,
            maintenanceWindows = activeAndUpcomingWindows.take(MAINTENANCE_WINDOWS_LIMIT),
            moreMaintenanceWindows = (activeAndUpcomingWindows.size - MAINTENANCE_WINDOWS_LIMIT).coerceAtLeast(0),
        )
    }

    private fun List<MaintenanceWindowDetailsDto>.activeAndUpcoming() =
        getCurrentTimestamp().plus(MAINTENANCE_LOOKAHEAD).let { lookaheadEnd ->
            this
                .filter { window ->
                    window.enabled && (window.active || window.nextStart?.isBefore(lookaheadEnd) == true)
                }
                // The active ones first, then the upcoming ones in the order they start
                .sortedWith(compareByDescending<MaintenanceWindowDetailsDto> { it.active }.thenBy { it.nextStart })
        }
}

private data class IncidentDtoKey(
    val incidentType: IncidentType,
    val monitorId: Long,
    val startedAt: OffsetDateTime,
)

private val IncidentDto.key: IncidentDtoKey
    get() = IncidentDtoKey(incidentType, monitorId, startedAt)

private val IncidentDto.numericMonitorId: NumericMonitorID
    get() = NumericMonitorID(incidentType.monitorType, monitorId)
