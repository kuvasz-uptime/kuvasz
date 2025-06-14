package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.MonitorConfig
import com.kuvaszuptime.kuvasz.models.dto.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

@Singleton
class MonitorImporter(
    private val integrationIdValidator: IntegrationIdValidator,
    private val monitorRepository: MonitorRepository,
    private val dslContext: DSLContext,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    fun importMonitorConfigs(monitorConfigs: List<MonitorConfig>): MonitorImportResultDto =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val upsertedMonitorIds = monitorConfigs.map { importedMonitor ->
                // Validating the monitor's integrations to ensure they are configured correctly
                val validatedIntegrations =
                    integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())

                // Upserting the monitor from the provided configs
                monitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
            }
            logger.info("Loaded ${monitorConfigs.size} monitors from external config")

            // Removing all monitors that are not in the provided configs
            val deletedCnt = monitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)
            logger.info("Deleted $deletedCnt monitors that were not in the external config")

            MonitorImportResultDto(
                receivedMonitorCnt = monitorConfigs.size,
                importedMonitorCnt = upsertedMonitorIds.size,
                deletedMonitorCount = deletedCnt,
            )
        }
}
