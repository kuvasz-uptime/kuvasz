package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import jakarta.inject.Singleton

@Singleton
class MaintenanceWindowService(
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
    private val calculator: MaintenanceWindowCalculator,
) {

    fun isUnderMaintenance(monitorId: MonitorID, category: String?): Boolean =
        maintenanceWindowRepository.findActiveCandidatesForMonitor(monitorId, category).any { calculator.isActive(it) }

    fun getWindowsForMonitor(monitorId: MonitorID, category: String?): List<MaintenanceWindowDetailsDto> =
        maintenanceWindowRepository.findActiveCandidatesForMonitor(monitorId, category)
            .map { it.toDetailsDto(calculator) }

    /**
     * Batch variant of [getWindowsForMonitor] for many monitors: resolves the affecting windows for all of them with a
     * single query, keyed by monitor. Use it on list/dashboard paths to avoid a query per monitor. Every requested
     * monitor gets an entry, even if it has no windows.
     */
    fun getWindowsForMonitors(
        monitorsWithCategory: Map<MonitorID, String?>,
    ): Map<MonitorID, List<MaintenanceWindowDetailsDto>> =
        maintenanceWindowRepository.findActiveCandidatesForMonitors(monitorsWithCategory)
            .mapValues { (_, windows) -> windows.map { it.toDetailsDto(calculator) } }
}
