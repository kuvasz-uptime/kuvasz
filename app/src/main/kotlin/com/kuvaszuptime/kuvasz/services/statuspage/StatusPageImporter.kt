package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.config.StatusPageConfig
import com.kuvaszuptime.kuvasz.models.dto.import.StatusPageImportResultDto
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
    private val dslContext: DSLContext,
) {
    companion object {
        private val logger = loggerFor<StatusPageImporter>()
    }

    fun importStatusPageConfigs(statusPageConfigs: List<StatusPageConfig>): StatusPageImportResultDto =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val upsertedStatusPageIds = statusPageConfigs.map { pageToImport ->
                // Validating the status page's monitors to ensure they are configured correctly
                val validatedMonitors = monitorIdValidator.validateMonitorIds(pageToImport.monitors.orEmpty())

                // Upserting the status page from the provided configs
                statusPageRepository.upsert(pageToImport.toStatusPageRecord(validatedMonitors), txCtx).id
            }
            logger.info("Loaded ${statusPageConfigs.size} status pages from external config")

            // Removing all status pages that are not in the provided configs
            val deletedCnt = statusPageRepository.deleteAllExcept(ignoredIds = upsertedStatusPageIds, txCtx)
            if (deletedCnt > 0) {
                logger.info("Deleted $deletedCnt status pages that were not in the external config")
            }

            StatusPageImportResultDto(
                receivedStatusPageCnt = statusPageConfigs.size,
                importedStatusPageCnt = upsertedStatusPageIds.size,
                deletedStatusPageCount = deletedCnt,
            )
        }
}
