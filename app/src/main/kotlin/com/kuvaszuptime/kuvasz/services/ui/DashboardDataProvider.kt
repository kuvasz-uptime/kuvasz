package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.models.dashboard.DashboardOverview
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowActions
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class DashboardDataProvider(
    private val statCalculator: StatCalculator,
    private val incidentRepository: IncidentRepository,
    private val maintenanceWindowActions: MaintenanceWindowActions,
) {

    companion object {
        const val RECENT_INCIDENTS_LIMIT = 6
        const val MAINTENANCE_WINDOWS_LIMIT = 5
        val MAINTENANCE_LOOKAHEAD: Duration = Duration.ofDays(7)
    }

    /**
     * Collects everything the dashboard shows over the given [period].
     */
    fun getOverview(period: Duration): DashboardOverview {
        // Ongoing incidents are always part of the result, no matter when they started
        val (ongoingIncidents, resolvedIncidents) = incidentRepository
            .getIncidents(period = period, includeResolved = true)
            .partition { it.status == IncidentStatus.ONGOING }
        // The ongoing ones come first, so the list is only topped up with the resolved ones if there is room left
        val recentIncidents = ongoingIncidents.sortedByDescending { it.startedAt } +
            resolvedIncidents.sortedByDescending { it.endedAt }

        return DashboardOverview(
            period = period,
            uptimeStats = statCalculator.calculateDashboardUptimeStats(period),
            recentIncidents = recentIncidents.take(RECENT_INCIDENTS_LIMIT),
            maintenanceWindows = getActiveAndUpcomingMaintenanceWindows(),
            maintenanceLookahead = MAINTENANCE_LOOKAHEAD,
        )
    }

    private fun getActiveAndUpcomingMaintenanceWindows() =
        getCurrentTimestamp().plus(MAINTENANCE_LOOKAHEAD).let { lookaheadEnd ->
            maintenanceWindowActions.getMaintenanceWindows()
                .filter { window ->
                    window.enabled && (window.active || window.nextStart?.isBefore(lookaheadEnd) == true)
                }
                // The active ones first, then the upcoming ones in the order they start
                .sortedWith(compareByDescending<MaintenanceWindowDetailsDto> { it.active }.thenBy { it.nextStart })
                .take(MAINTENANCE_WINDOWS_LIMIT)
        }
}
