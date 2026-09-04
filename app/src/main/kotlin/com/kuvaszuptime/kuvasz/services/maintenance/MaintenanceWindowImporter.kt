package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.models.dto.importing.MaintenanceWindowImportResultDto
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowCreator
import com.kuvaszuptime.kuvasz.models.maintenance.toMaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.maintenance.validateScheduleConsistency
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageCacheInvalidator
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import com.kuvaszuptime.kuvasz.validation.MonitorIdValidator
import com.kuvaszuptime.kuvasz.validation.ResolvedIntegrationIds
import com.kuvaszuptime.kuvasz.validation.ResolvedMonitorIds
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
    private val integrationIdValidator: IntegrationIdValidator,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
    private val maintenanceWindowScheduler: MaintenanceWindowScheduler,
    private val statusPageCacheInvalidator: StatusPageCacheInvalidator,
    private val dslContext: DSLContext,
) {
    companion object {
        private val logger = loggerFor<MaintenanceWindowImporter>()
    }

    fun importMaintenanceWindowConfigs(
        maintenanceWindowConfigs: List<MaintenanceWindowCreator>,
        dryRun: Boolean,
    ): MaintenanceWindowImportResultDto =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val ignoredMonitors = mutableSetOf<String>()
            val ignoredIntegrations = mutableSetOf<String>()

            val upsertedWindows = maintenanceWindowConfigs.map { windowToImport ->
                windowToImport.validateScheduleConsistency()
                val resolvedMonitors = windowToImport.resolveMonitors()
                val resolvedIntegrations = windowToImport.resolveIntegrations()
                ignoredMonitors.addAll(resolvedMonitors.ignored)
                ignoredIntegrations.addAll(resolvedIntegrations.ignored)

                maintenanceWindowRepository.upsert(
                    windowToImport.toMaintenanceWindowRecord(resolvedMonitors.valid, resolvedIntegrations.valid),
                    txCtx,
                )
            }
            logger.info("Loaded ${maintenanceWindowConfigs.size} maintenance windows from config, dryRun: $dryRun")

            val deleted = maintenanceWindowRepository.deleteAllExcept(ignoredIds = upsertedWindows.map { it.id }, txCtx)
            if (deleted.isNotEmpty()) {
                logger.info("Deleted ${deleted.size} maintenance windows not in the external config, dryRun: $dryRun")
            }

            if (dryRun) txCtx.connection { it.rollback() }

            MaintenanceWindowImportResultDto(
                receivedCnt = maintenanceWindowConfigs.size,
                dryRun = dryRun,
                imported = upsertedWindows.map { it.name },
                deleted = deleted,
                ignoredMonitors = ignoredMonitors.toList(),
                ignoredIntegrations = ignoredIntegrations.toList(),
            )
        }

    fun importMaintenanceWindowsFromBackup(
        maintenanceWindowConfigs: List<MaintenanceWindowCreator>,
        dryRun: Boolean,
    ): MaintenanceWindowImportResultDto {
        if (maintenanceWindowConfigs.isEmpty()) {
            return MaintenanceWindowImportResultDto(receivedCnt = 0, dryRun = dryRun)
        }
        val result = importMaintenanceWindowConfigs(maintenanceWindowConfigs, dryRun)
        if (!dryRun) {
            maintenanceWindowScheduler.reschedule()
            statusPageCacheInvalidator.invalidateAllCaches()
        }
        return result
    }

    private fun MaintenanceWindowCreator.resolveMonitors(): ResolvedMonitorIds {
        val rawMonitors = monitors.orEmpty()
        // Validates the format strictly (throws on a malformed ID), but drops well-formed references to
        // non-existing monitors and reports them as ignored.
        val valid = monitorIdValidator.validateMonitorIds(rawMonitors)
        val validAsStrings = valid.map { it.toString() }.toSet()
        val ignored = rawMonitors.filterNot { validAsStrings.contains(it) }
        if (ignored.isNotEmpty()) {
            logger.warn(
                "Ignoring non-existing monitors $ignored referenced by maintenance window '$name'"
            )
        }
        return ResolvedMonitorIds(valid = valid, ignored = ignored)
    }

    private fun MaintenanceWindowCreator.resolveIntegrations(): ResolvedIntegrationIds {
        val resolved = integrationIdValidator.resolveIntegrationIds(integrations.orEmpty())
        if (resolved.ignored.isNotEmpty()) {
            logger.warn(
                "Ignoring non-configured integrations ${resolved.ignored} referenced by maintenance window '$name'"
            )
        }
        return resolved
    }
}
