package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.models.dto.import.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.monitor.http.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

@Singleton
class MonitorImporter(
    private val integrationIdValidator: IntegrationIdValidator,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val dslContext: DSLContext,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    fun importHttpMonitorConfigs(monitorConfigs: List<HttpMonitorConfig>): MonitorImportResultDto =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val upsertedMonitorIds = monitorConfigs.map { importedMonitor ->
                // Validating the monitor's integrations to ensure they are configured correctly
                val validatedIntegrations =
                    integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())

                // Upserting the monitor from the provided configs
                httpMonitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
            }
            logger.info("Loaded ${monitorConfigs.size} monitors from external config")

            // Removing all monitors that are not in the provided configs
            val deletedCnt = httpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)
            logger.info("Deleted $deletedCnt monitors that were not in the external config")

            MonitorImportResultDto(
                receivedMonitorCnt = monitorConfigs.size,
                importedMonitorCnt = upsertedMonitorIds.size,
                deletedMonitorCount = deletedCnt,
            )
        }
}
