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

    fun isUnderMaintenance(monitorId: MonitorID): Boolean =
        maintenanceWindowRepository.findActiveCandidatesForMonitor(monitorId).any { calculator.isActive(it) }

    fun getWindowsForMonitor(monitorId: MonitorID): List<MaintenanceWindowDetailsDto> =
        maintenanceWindowRepository.findActiveCandidatesForMonitor(monitorId).map { it.toDetailsDto(calculator) }

    /**
     * Batch variant of [getWindowsForMonitor] for many monitors: resolves the affecting windows for all of them with a
     * single query, keyed by monitor. Use it on list/dashboard paths to avoid a query per monitor. Every requested
     * monitor gets an entry, even if it has no windows.
     */
    fun getWindowsForMonitors(
        monitorIds: List<MonitorID>,
    ): Map<MonitorID, List<MaintenanceWindowDetailsDto>> =
        maintenanceWindowRepository.findActiveCandidatesForMonitors(monitorIds)
            .mapValues { (_, windows) -> windows.map { it.toDetailsDto(calculator) } }
}
