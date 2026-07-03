package com.kuvaszuptime.kuvasz.services.monitor.importer

import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportDto
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportResultDto

interface MonitorImportService {
    fun importMonitors(importDto: MonitorImportDto, dryRun: Boolean = false): MonitorImportResultDto
}
