package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.models.dto.importing.ImportResultDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.statuspage.StatusPageCreator
import com.kuvaszuptime.kuvasz.models.statuspage.toStatusPageRecord
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.validation.MonitorIdValidator
import jakarta.inject.Singleton
import org.jooq.DSLContext

/**
 * Imports the status pages from the provided configurations, which are typically coming from a YAML file.
 */
@Singleton
class StatusPageImporter(
    private val monitorIdValidator: MonitorIdValidator,
    private val statusPageRepository: StatusPageRepository,
    private val statusPageDataActions: StatusPageDataActions,
    private val dslContext: DSLContext,
) {
    companion object {
        private val logger = loggerFor<StatusPageImporter>()
    }

    fun importStatusPageConfigs(
        statusPageConfigs: List<StatusPageCreator>,
        dryRun: Boolean,
    ): ImportResultDto =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val upsertedStatusPageIds = statusPageConfigs.map { pageToImport ->
                // Validating the status page's monitors, dropping (with a warning) the ones that don't exist
                val validatedMonitors = pageToImport.resolveMonitors()

                // Upserting the status page from the provided configs
                statusPageRepository.upsert(pageToImport.toStatusPageRecord(validatedMonitors), txCtx).id
            }
            logger.info("Loaded ${statusPageConfigs.size} status pages from external config, dryrun: $dryRun")

            // Removing all status pages that are not in the provided configs
            val deletedCnt = statusPageRepository.deleteAllExcept(ignoredIds = upsertedStatusPageIds, txCtx)
            if (deletedCnt > 0) {
                logger.info("Deleted $deletedCnt status pages that were not in the external config, dryrun: $dryRun")
            }

            if (dryRun) txCtx.connection { it.rollback() }

            ImportResultDto(
                receivedCnt = statusPageConfigs.size,
                importedCnt = upsertedStatusPageIds.size,
                deletedCnt = deletedCnt,
                dryRun = dryRun,
            )
        }

    fun importStatusPagesFromBackup(
        statusPageConfigs: List<StatusPageCreator>,
        dryRun: Boolean,
    ): ImportResultDto {
        if (statusPageConfigs.isEmpty()) {
            return ImportResultDto(receivedCnt = 0, importedCnt = 0, deletedCnt = 0, dryRun = dryRun)
        }
        val result = importStatusPageConfigs(statusPageConfigs, dryRun)
        if (!dryRun) {
            statusPageDataActions.invalidateAllCaches()
        }
        return result
    }

    private fun StatusPageCreator.resolveMonitors(): Set<MonitorID> {
        val rawMonitors = monitors.orEmpty()
        val validated = monitorIdValidator.validateMonitorIds(rawMonitors)
        val validatedAsStrings = validated.map { it.toString() }.toSet()
        val dropped = rawMonitors.filterNot { validatedAsStrings.contains(it) }
        if (dropped.isNotEmpty()) {
            logger.warn("Ignoring non-existing monitors $dropped referenced by status page '$title'")
        }
        return validated
    }
}
