package com.kuvaszuptime.kuvasz.services.monitor.import

import com.kuvaszuptime.kuvasz.models.dto.import.MonitorImportDto
import com.kuvaszuptime.kuvasz.models.dto.import.MonitorImportResultDto

interface MonitorImportService {
    fun importMonitors(importDto: MonitorImportDto, dryRun: Boolean = false): MonitorImportResultDto
}
