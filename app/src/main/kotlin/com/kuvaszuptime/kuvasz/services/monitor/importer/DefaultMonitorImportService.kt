package com.kuvaszuptime.kuvasz.services.monitor.importer

import com.kuvaszuptime.kuvasz.models.dto.importing.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.IcmpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportDto
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.dto.importing.PushMonitorImportAdapter
import com.kuvaszuptime.kuvasz.services.monitor.MonitorImporter
import jakarta.inject.Singleton
import jakarta.validation.ValidationException
import jakarta.validation.Validator

@Singleton
class DefaultMonitorImportService(
    private val validator: Validator,
    private val monitorImporter: MonitorImporter,
) : MonitorImportService {

    override fun importMonitors(importDto: MonitorImportDto, dryRun: Boolean): MonitorImportResultDto {
        val validatedImport = ValidatedMonitorImport(
            httpMonitors = importDto.httpMonitors.orEmpty().map { HttpMonitorImportAdapter(it) },
            pushMonitors = importDto.pushMonitors.orEmpty().map { PushMonitorImportAdapter(it) },
            icmpMonitors = importDto.icmpMonitors.orEmpty().map { IcmpMonitorImportAdapter(it) },
        )
        validatedImport.allAdapters().forEach { adapter ->
            val violations = validator.validate(adapter)
            if (violations.isNotEmpty()) {
                throw ValidationException(
                    violations.joinToString(", ") { "${it.propertyPath}: ${it.message}" }
                )
            }
        }
        return monitorImporter.importMonitorConfigs(validatedImport, dryRun)
    }
}
