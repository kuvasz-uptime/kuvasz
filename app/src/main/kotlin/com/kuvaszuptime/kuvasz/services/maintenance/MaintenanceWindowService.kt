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
}
