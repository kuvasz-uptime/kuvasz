package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.config.MaintenanceWindowConfig
import com.kuvaszuptime.kuvasz.models.dto.import.MaintenanceWindowImportResultDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.maintenance.toMaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.maintenance.validateScheduleConsistency
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.validation.MonitorIdValidator
import jakarta.inject.Singleton
import org.jooq.DSLContext

/**
 * Imports maintenance windows from the provided configurations, which are typically coming from a YAML file.
 *
 * Unlike status pages, the monitor and integration references are validated leniently: references that point to
 * non-existent monitors or non-configured integrations are dropped (with a warning) instead of failing the import,
 * so a single stale reference cannot break the application's startup.
 */
@Singleton
class MaintenanceWindowImporter(
    private val monitorIdValidator: MonitorIdValidator,
    private val integrationRepository: IntegrationRepository,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
    private val dslContext: DSLContext,
) {
    companion object {
        private val logger = loggerFor<MaintenanceWindowImporter>()
    }

    fun importMaintenanceWindowConfigs(
        maintenanceWindowConfigs: List<MaintenanceWindowConfig>,
    ): MaintenanceWindowImportResultDto =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val configuredIntegrations = integrationRepository.configuredIntegrations.keys

            val upsertedIds = maintenanceWindowConfigs.map { windowToImport ->
                windowToImport.validateScheduleConsistency()
                val validatedMonitors = windowToImport.resolveMonitors()
                val validatedIntegrations = windowToImport.resolveIntegrations(configuredIntegrations)

                maintenanceWindowRepository
                    .upsert(windowToImport.toMaintenanceWindowRecord(validatedMonitors, validatedIntegrations), txCtx)
                    .id
            }
            logger.info("Loaded ${maintenanceWindowConfigs.size} maintenance windows from external config")

            val deletedCnt = maintenanceWindowRepository.deleteAllExcept(ignoredIds = upsertedIds, txCtx)
            if (deletedCnt > 0) {
                logger.info("Deleted $deletedCnt maintenance windows that were not in the external config")
            }

            MaintenanceWindowImportResultDto(
                receivedMaintenanceWindowCnt = maintenanceWindowConfigs.size,
                importedMaintenanceWindowCnt = upsertedIds.size,
                deletedMaintenanceWindowCnt = deletedCnt,
            )
        }

    private fun MaintenanceWindowConfig.resolveMonitors(): Set<MonitorID> {
        val rawMonitors = monitors.orEmpty()
        val validated = monitorIdValidator.validateMonitorIds(rawMonitors)
        val validatedAsStrings = validated.map { it.toString() }.toSet()
        val dropped = rawMonitors.filterNot { validatedAsStrings.contains(it) }
        if (dropped.isNotEmpty()) {
            logger.warn(
                "Ignoring non-existing monitors $dropped referenced by maintenance window '$name'"
            )
        }
        return validated
    }

    private fun MaintenanceWindowConfig.resolveIntegrations(
        configuredIntegrations: Set<IntegrationID>,
    ): Set<IntegrationID> {
        val rawIntegrations = integrations.orEmpty()
        val validated = rawIntegrations
            .mapNotNull { IntegrationID.fromString(it) }
            .filter { configuredIntegrations.contains(it) }
            .toSet()
        if (validated.size != rawIntegrations.size) {
            val validatedAsStrings = validated.map { it.toString() }.toSet()
            val dropped = rawIntegrations.filterNot { validatedAsStrings.contains(it) }
            logger.warn(
                "Ignoring non-configured integrations $dropped referenced by maintenance window '$name'"
            )
        }
        return validated
    }
}
