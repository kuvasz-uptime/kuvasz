package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.config.IcmpMonitorConfig
import com.kuvaszuptime.kuvasz.config.PushMonitorConfig
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.monitor.http.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.icmp.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.push.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.services.monitor.importer.ImportStrategy
import com.kuvaszuptime.kuvasz.services.monitor.importer.ValidatedMonitorImport
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
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
    private val importStrategy: ImportStrategy,
) {

    companion object {
        private val logger = loggerFor<MonitorImporter>()
    }

    /**
     * New orchestrated import path. The legacy per-type methods below are kept for
     * the external YAML config bootstrap path; they are the reason this class still
     * injects the three repositories and IntegrationIdValidator.
     */
    @Suppress("TooGenericExceptionCaught")
    fun importMonitorConfigs(validatedImport: ValidatedMonitorImport, dryRun: Boolean = false): MonitorImportResultDto =
        try {
            dslContext.transactionResultWithError { txCtx ->
                val perTypeResults = importStrategy.execute(validatedImport, txCtx)
                val result = MonitorImportResultDto(
                    receivedMonitorCnt = perTypeResults.sumOf { it.receivedMonitorCnt },
                    importedMonitorCnt = perTypeResults.sumOf { it.importedMonitorCnt },
                    deletedMonitorCount = perTypeResults.sumOf { it.deletedMonitorCount },
                    dryRun = dryRun,
                    perTypeResults = perTypeResults,
                )

                if (dryRun) throw DryRunRollbackException(result)
                result
            }
        } catch (e: DryRunRollbackException) {
            e.result
        } catch (e: RuntimeException) {
            // Defensive: some transaction providers wrap the rollback marker in a RuntimeException.
            // A wrapped dry-run marker returns the result; any other RuntimeException is rethrown.
            val cause = e.cause
            if (cause is DryRunRollbackException) cause.result else throw e
        }

    fun importHttpMonitorConfigs(monitorConfigs: List<HttpMonitorConfig>): MonitorImportResultDto =
        dslContext.transactionResult { config ->
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
                val validatedIntegrations =
                    integrationIdValidator.validateIntegrationIds(importedMonitor.integrations.orEmpty())
                pushMonitorRepository.upsert(importedMonitor.toMonitorRecord(validatedIntegrations), txCtx).id
            }
            logger.info("Loaded ${monitorConfigs.size} push monitors from external config")

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

    private class DryRunRollbackException(val result: MonitorImportResultDto) : RuntimeException()
}
