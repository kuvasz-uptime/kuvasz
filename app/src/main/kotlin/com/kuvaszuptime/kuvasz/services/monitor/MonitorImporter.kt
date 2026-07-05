package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.config.IcmpMonitorConfig
import com.kuvaszuptime.kuvasz.config.PushMonitorConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorTypeImportResult
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.http.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.icmp.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.push.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class MonitorImporter(
    private val integrationIdValidator: IntegrationIdValidator,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val dslContext: DSLContext,
) {

    companion object {
        private val logger = loggerFor<MonitorImporter>()
    }

    fun importHttpMonitorConfigs(
        monitorConfigs: List<HttpMonitorCreator>,
        dryRun: Boolean = false,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        val txCtx = config.dsl()
        val upsertedMonitorIds = monitorConfigs.map { importedMonitor ->
            val validatedIntegrations =
                integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())
            httpMonitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
        }
        logger.info("Loaded ${monitorConfigs.size} HTTP monitors from external config")

        val deletedCnt = httpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)
        if (deletedCnt > 0) {
            logger.info("Deleted $deletedCnt HTTP monitors that were not in the external config")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        MonitorTypeImportResult(
            monitorType = MonitorType.HTTP_SSL,
            receivedMonitorCnt = monitorConfigs.size,
            importedMonitorCnt = upsertedMonitorIds.size,
            deletedMonitorCount = deletedCnt,
        )
    }

    fun importPushMonitorConfigs(
        monitorConfigs: List<PushMonitorCreator>,
        dryRun: Boolean = false,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        val txCtx = config.dsl()
        val upsertedMonitorIds = monitorConfigs.map { importedMonitor ->
            val validatedIntegrations =
                integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())
            pushMonitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
        }
        logger.info("Loaded ${monitorConfigs.size} push monitors from external config")

        val deletedCnt = pushMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)
        if (deletedCnt > 0) {
            logger.info("Deleted $deletedCnt push monitors that were not in the external config")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        MonitorTypeImportResult(
            monitorType = MonitorType.PUSH,
            receivedMonitorCnt = monitorConfigs.size,
            importedMonitorCnt = upsertedMonitorIds.size,
            deletedMonitorCount = deletedCnt,
        )
    }

    fun importIcmpMonitorConfigs(
        monitorConfigs: List<IcmpMonitorCreator>,
        dryRun: Boolean = false,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
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

        if (dryRun) txCtx.connection { it.rollback() }

        MonitorTypeImportResult(
            monitorType = MonitorType.ICMP,
            receivedMonitorCnt = monitorConfigs.size,
            importedMonitorCnt = upsertedMonitorIds.size,
            deletedMonitorCount = deletedCnt,
        )
    }
}
