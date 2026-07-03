package com.kuvaszuptime.kuvasz.services.monitor.import

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.import.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.import.IcmpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.import.MonitorTypeImportResult
import com.kuvaszuptime.kuvasz.models.dto.import.PushMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.monitor.http.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.icmp.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.push.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class ReplaceAllImportStrategy(
    private val integrationIdValidator: IntegrationIdValidator,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
) : ImportStrategy {

    override fun execute(validatedImport: ValidatedMonitorImport, txCtx: DSLContext): List<MonitorTypeImportResult> =
        listOfNotNull(
            importHttpMonitors(validatedImport.httpMonitors, txCtx),
            importPushMonitors(validatedImport.pushMonitors, txCtx),
            importIcmpMonitors(validatedImport.icmpMonitors, txCtx),
        )

    private fun importHttpMonitors(
        monitorConfigs: List<HttpMonitorImportAdapter>,
        txCtx: DSLContext,
    ): MonitorTypeImportResult? {
        if (monitorConfigs.isEmpty()) return null
        val upsertedMonitorIds = monitorConfigs.map { importedMonitor ->
            val validatedIntegrations =
                integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())
            httpMonitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
        }

        val deletedCnt = httpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)

        return MonitorTypeImportResult(
            monitorType = MonitorType.HTTP_SSL,
            receivedMonitorCnt = monitorConfigs.size,
            importedMonitorCnt = upsertedMonitorIds.size,
            deletedMonitorCount = deletedCnt,
        )
    }

    private fun importPushMonitors(
        monitorConfigs: List<PushMonitorImportAdapter>,
        txCtx: DSLContext,
    ): MonitorTypeImportResult? {
        if (monitorConfigs.isEmpty()) return null
        val upsertedMonitorIds = monitorConfigs.map { importedMonitor ->
            val validatedIntegrations =
                integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())
            pushMonitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
        }

        val deletedCnt = pushMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)

        return MonitorTypeImportResult(
            monitorType = MonitorType.PUSH,
            receivedMonitorCnt = monitorConfigs.size,
            importedMonitorCnt = upsertedMonitorIds.size,
            deletedMonitorCount = deletedCnt,
        )
    }

    private fun importIcmpMonitors(
        monitorConfigs: List<IcmpMonitorImportAdapter>,
        txCtx: DSLContext,
    ): MonitorTypeImportResult? {
        if (monitorConfigs.isEmpty()) return null
        val upsertedMonitorIds = monitorConfigs.map { importedMonitor ->
            val validatedIntegrations =
                integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())
            icmpMonitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
        }

        val deletedCnt = icmpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds, txCtx)

        return MonitorTypeImportResult(
            monitorType = MonitorType.ICMP,
            receivedMonitorCnt = monitorConfigs.size,
            importedMonitorCnt = upsertedMonitorIds.size,
            deletedMonitorCount = deletedCnt,
        )
    }
}
