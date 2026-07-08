package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.models.dto.importing.StatusPageImportResultDto
import com.kuvaszuptime.kuvasz.models.statuspage.StatusPageCreator
import com.kuvaszuptime.kuvasz.models.statuspage.toStatusPageRecord
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.validation.MonitorIdValidator
import com.kuvaszuptime.kuvasz.validation.ResolvedMonitorIds
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
    ): StatusPageImportResultDto =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val ignoredMonitors = mutableSetOf<String>()
            val upsertedStatusPages = statusPageConfigs.map { pageToImport ->
                // Validating the status page's monitors, dropping (with a warning) the ones that don't exist
                val resolvedMonitors = pageToImport.resolveMonitors()
                ignoredMonitors.addAll(resolvedMonitors.ignored)

                // Upserting the status page from the provided configs
                statusPageRepository.upsert(pageToImport.toStatusPageRecord(resolvedMonitors.valid), txCtx)
            }
            logger.info("Loaded ${statusPageConfigs.size} status pages from external config, dryrun: $dryRun")

            // Removing all status pages that are not in the provided configs
            val deleted = statusPageRepository.deleteAllExcept(ignoredIds = upsertedStatusPages.map { it.id }, txCtx)
            if (deleted.isNotEmpty()) {
                logger.info("Deleted ${deleted.size} status pages that were not in the config, dryrun: $dryRun")
            }

            if (dryRun) txCtx.connection { it.rollback() }

            StatusPageImportResultDto(
                receivedCnt = statusPageConfigs.size,
                dryRun = dryRun,
                imported = upsertedStatusPages.map { it.title },
                deleted = deleted,
                ignoredMonitors = ignoredMonitors.toList(),
            )
        }

    fun importStatusPagesFromBackup(
        statusPageConfigs: List<StatusPageCreator>,
        dryRun: Boolean,
    ): StatusPageImportResultDto {
        if (statusPageConfigs.isEmpty()) {
            return StatusPageImportResultDto(receivedCnt = 0, dryRun = dryRun)
        }
        val result = importStatusPageConfigs(statusPageConfigs, dryRun)
        if (!dryRun) {
            statusPageDataActions.invalidateAllCaches()
        }
        return result
    }

    private fun StatusPageCreator.resolveMonitors(): ResolvedMonitorIds {
        val rawMonitors = monitors.orEmpty()
        // Validates the format strictly (throws on a malformed ID), but drops well-formed references to
        // non-existing monitors and reports them as ignored.
        val valid = monitorIdValidator.validateMonitorIds(rawMonitors)
        val validAsStrings = valid.map { it.toString() }.toSet()
        val ignored = rawMonitors.filterNot { validAsStrings.contains(it) }
        if (ignored.isNotEmpty()) {
            logger.warn("Ignoring non-existing monitors $ignored referenced by status page '$title'")
        }
        return ResolvedMonitorIds(valid = valid, ignored = ignored)
    }
}
