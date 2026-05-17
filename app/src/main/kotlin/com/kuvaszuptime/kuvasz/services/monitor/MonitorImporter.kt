package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.config.IcmpMonitorConfig
import com.kuvaszuptime.kuvasz.config.PushMonitorConfig
import com.kuvaszuptime.kuvasz.models.dto.import.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.monitor.http.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.icmp.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.push.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

@Singleton
class MonitorImporter(
    private val integrationIdValidator: IntegrationIdValidator,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
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
            logger.info("Loaded ${monitorConfigs.size} HTTP monitors from external config")

            // Removing all monitors that are not in the provided configs
            val deletedCnt = httpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)
            if (deletedCnt > 0) {
                logger.info("Deleted $deletedCnt HTTP monitors that were not in the external config")
            }

            MonitorImportResultDto(
                receivedMonitorCnt = monitorConfigs.size,
                importedMonitorCnt = upsertedMonitorIds.size,
                deletedMonitorCount = deletedCnt,
            )
        }

    fun importPushMonitorConfigs(monitorConfigs: List<PushMonitorConfig>): MonitorImportResultDto =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()

            val upsertedMonitorIds = monitorConfigs.map { importedMonitor ->
                // Validating the monitor's integrations to ensure they are configured correctly
                val validatedIntegrations =
                    integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())

                // Upserting the monitor from the provided configs
                pushMonitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
            }
            logger.info("Loaded ${monitorConfigs.size} push monitors from external config")

            // Removing all monitors that are not in the provided configs
            val deletedCnt = pushMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)
            if (deletedCnt > 0) {
                logger.info("Deleted $deletedCnt push monitors that were not in the external config")
            }

            MonitorImportResultDto(
                receivedMonitorCnt = monitorConfigs.size,
                importedMonitorCnt = upsertedMonitorIds.size,
                deletedMonitorCount = deletedCnt,
            )
        }

    fun importIcmpMonitorConfigs(monitorConfigs: List<IcmpMonitorConfig>): MonitorImportResultDto =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()

            val upsertedMonitorIds = monitorConfigs.map { importedMonitor ->
                val validatedIntegrations =
                    integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())

                icmpMonitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
            }
            logger.info("Loaded ${monitorConfigs.size} ICMP monitors from external config")

            val deletedCnt = icmpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)
            if (deletedCnt > 0) {
                logger.info("Deleted $deletedCnt ICMP monitors that were not in the external config")
            }

            MonitorImportResultDto(
                receivedMonitorCnt = monitorConfigs.size,
                importedMonitorCnt = upsertedMonitorIds.size,
                deletedMonitorCount = deletedCnt,
            )
        }
}
