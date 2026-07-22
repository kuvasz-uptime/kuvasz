package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorTypeImportResult
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.http.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.icmp.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.icmp.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.tcp.TcpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.tcp.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.tcp.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.dns.DnsCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpCheckScheduler
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import com.kuvaszuptime.kuvasz.validation.ResolvedIntegrationIds
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class MonitorImporter(
    private val integrationIdValidator: IntegrationIdValidator,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dslContext: DSLContext,
    private val httpCheckScheduler: HttpCheckScheduler,
    private val icmpCheckScheduler: IcmpCheckScheduler,
    private val tcpCheckScheduler: TcpCheckScheduler,
    private val dnsCheckScheduler: DnsCheckScheduler,
) {

    companion object {
        private val logger = loggerFor<MonitorImporter>()
    }

    fun batchImportMonitors(
        httpMonitorConfigs: List<HttpMonitorCreator>,
        pushMonitorConfigs: List<PushMonitorCreator>,
        icmpMonitorConfigs: List<IcmpMonitorCreator>,
        tcpMonitorConfigs: List<TcpMonitorCreator>,
        dryRun: Boolean,
    ): List<MonitorTypeImportResult> {
        val results = dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            listOfNotNull(
                httpMonitorConfigs.takeIf { it.isNotEmpty() }
                    ?.let { importHttpMonitorConfigs(it, dryRun, txCtx, lenientIntegrations = true) },
                pushMonitorConfigs.takeIf { it.isNotEmpty() }
                    ?.let { importPushMonitorConfigs(it, dryRun, txCtx, lenientIntegrations = true) },
                icmpMonitorConfigs.takeIf { it.isNotEmpty() }
                    ?.let { importIcmpMonitorConfigs(it, dryRun, txCtx, lenientIntegrations = true) },
                tcpMonitorConfigs.takeIf { it.isNotEmpty() }
                    ?.let { importTcpMonitorConfigs(it, dryRun, txCtx, lenientIntegrations = true) },
            )
        }
        if (!dryRun) {
            results.forEach { rescheduleChecksFor(it.monitorType) }
        }

        return results
    }

    private fun rescheduleChecksFor(monitorType: MonitorType) {
        when (monitorType) {
            MonitorType.HTTP_SSL -> httpCheckScheduler.run {
                removeAllChecks()
                initialize()
            }

            MonitorType.ICMP -> icmpCheckScheduler.run {
                removeAllChecks()
                initialize()
            }

            MonitorType.TCP -> tcpCheckScheduler.run {
                removeAllChecks()
                initialize()
            }

            MonitorType.PUSH -> Unit

            MonitorType.DNS -> dnsCheckScheduler.run {
                removeAllChecks()
                initialize()
            }
        }
    }

    /**
     * Entry point for loading monitors from the startup YAML config. Integration references are validated strictly:
     * a non-existing integration fails the import, so a misconfigured YAML is caught at startup instead of silently
     * dropping references.
     */
    fun importHttpMonitorConfigs(
        monitorConfigs: List<HttpMonitorCreator>,
        dryRun: Boolean,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        importHttpMonitorConfigs(monitorConfigs, dryRun, config.dsl(), lenientIntegrations = false)
    }

    fun importPushMonitorConfigs(
        monitorConfigs: List<PushMonitorCreator>,
        dryRun: Boolean,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        importPushMonitorConfigs(monitorConfigs, dryRun, config.dsl(), lenientIntegrations = false)
    }

    fun importIcmpMonitorConfigs(
        monitorConfigs: List<IcmpMonitorCreator>,
        dryRun: Boolean,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        importIcmpMonitorConfigs(monitorConfigs, dryRun, config.dsl(), lenientIntegrations = false)
    }

    fun importTcpMonitorConfigs(
        monitorConfigs: List<TcpMonitorCreator>,
        dryRun: Boolean,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        importTcpMonitorConfigs(monitorConfigs, dryRun, config.dsl(), lenientIntegrations = false)
    }

    private fun resolveIntegrations(rawIds: List<String>, lenient: Boolean): ResolvedIntegrationIds =
        if (lenient) {
            integrationIdValidator.resolveIntegrationIds(rawIds)
        } else {
            ResolvedIntegrationIds(valid = integrationIdValidator.validateIntegrationIds(rawIds), ignored = emptyList())
        }

    private fun importHttpMonitorConfigs(
        monitorConfigs: List<HttpMonitorCreator>,
        dryRun: Boolean,
        txCtx: DSLContext,
        lenientIntegrations: Boolean,
    ): MonitorTypeImportResult {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            httpMonitorRepository.upsert(importedMonitor.toMonitorRecord(resolved.valid), txCtx)
        }
        logger.info("Loaded ${monitorConfigs.size} HTTP monitors from external config, dryrun: $dryRun")

        val deleted = httpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info("Deleted ${deleted.size} HTTP monitors that were not in the external config, dryrun: $dryRun")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return MonitorTypeImportResult(
            monitorType = MonitorType.HTTP_SSL,
            receivedCnt = monitorConfigs.size,
            imported = upsertedMonitors.map { it.monitorId() },
            deleted = deleted,
            ignoredIntegrations = ignoredIntegrations.toList(),
        )
    }

    private fun importPushMonitorConfigs(
        monitorConfigs: List<PushMonitorCreator>,
        dryRun: Boolean,
        txCtx: DSLContext,
        lenientIntegrations: Boolean,
    ): MonitorTypeImportResult {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            pushMonitorRepository.upsert(importedMonitor.toMonitorRecord(resolved.valid), txCtx)
        }
        logger.info("Loaded ${monitorConfigs.size} push monitors from external config, dryrun: $dryRun")

        val deleted = pushMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info("Deleted ${deleted.size} push monitors that were not in the external config, dryrun: $dryRun")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return MonitorTypeImportResult(
            monitorType = MonitorType.PUSH,
            receivedCnt = monitorConfigs.size,
            imported = upsertedMonitors.map { it.monitorId() },
            deleted = deleted,
            ignoredIntegrations = ignoredIntegrations.toList(),
        )
    }

    private fun importIcmpMonitorConfigs(
        monitorConfigs: List<IcmpMonitorCreator>,
        dryRun: Boolean,
        txCtx: DSLContext,
        lenientIntegrations: Boolean,
    ): MonitorTypeImportResult {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            icmpMonitorRepository.upsert(importedMonitor.toMonitorRecord(resolved.valid), txCtx)
        }
        logger.info("Loaded ${monitorConfigs.size} ICMP monitors from external config, dryrun: $dryRun")

        val deleted = icmpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info("Deleted ${deleted.size} ICMP monitors that were not in the external config, dryrun: $dryRun")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return MonitorTypeImportResult(
            monitorType = MonitorType.ICMP,
            receivedCnt = monitorConfigs.size,
            imported = upsertedMonitors.map { it.monitorId() },
            deleted = deleted,
            ignoredIntegrations = ignoredIntegrations.toList(),
        )
    }

    private fun importTcpMonitorConfigs(
        monitorConfigs: List<TcpMonitorCreator>,
        dryRun: Boolean,
        txCtx: DSLContext,
        lenientIntegrations: Boolean,
    ): MonitorTypeImportResult {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            tcpMonitorRepository.upsert(importedMonitor.toMonitorRecord(resolved.valid), txCtx)
        }
        logger.info("Loaded ${monitorConfigs.size} TCP monitors from external config, dryrun: $dryRun")

        val deleted = tcpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info("Deleted ${deleted.size} TCP monitors that were not in the external config, dryrun: $dryRun")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return MonitorTypeImportResult(
            monitorType = MonitorType.TCP,
            receivedCnt = monitorConfigs.size,
            imported = upsertedMonitors.map { it.monitorId() },
            deleted = deleted,
            ignoredIntegrations = ignoredIntegrations.toList(),
        )
    }
}
