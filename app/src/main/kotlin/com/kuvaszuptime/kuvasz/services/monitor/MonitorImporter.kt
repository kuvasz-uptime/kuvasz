package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorTypeImportResult
import com.kuvaszuptime.kuvasz.models.events.MonitorDeleteEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.dns.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.dns.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.dns.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.http.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.http.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.icmp.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.icmp.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.icmp.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.push.affectsFailureCounting
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.tcp.TcpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.tcp.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.tcp.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.tcp.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.MonitorCheckScheduler
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
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
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
    private val icmpMetricsLogRepository: IcmpMetricsLogRepository,
    private val tcpMetricsLogRepository: TcpMetricsLogRepository,
    private val dnsMetricsLogRepository: DnsMetricsLogRepository,
    private val pendingFailureRepository: PendingFailureRepository,
    private val dslContext: DSLContext,
    private val checkSchedulers: List<MonitorCheckScheduler>,
    private val eventDispatcher: EventDispatcher,
    private val statusPageDataActions: StatusPageDataActions,
) {

    companion object {
        private val logger = loggerFor<MonitorImporter>()
    }

    /**
     * The outcome of importing a single monitor type: the result to report back to the caller, and the changes to
     * announce once they are committed.
     */
    private data class ImportOutcome(
        val result: MonitorTypeImportResult,
        val lifecycleEvents: List<MonitorLifecycleEvent>,
    )

    fun batchImportMonitors(
        httpMonitorConfigs: List<HttpMonitorCreator>,
        pushMonitorConfigs: List<PushMonitorCreator>,
        icmpMonitorConfigs: List<IcmpMonitorCreator>,
        tcpMonitorConfigs: List<TcpMonitorCreator>,
        dnsMonitorConfigs: List<DnsMonitorCreator>,
        dryRun: Boolean,
    ): List<MonitorTypeImportResult> {
        val outcomes = dslContext.transactionResult { config ->
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
                dnsMonitorConfigs.takeIf { it.isNotEmpty() }
                    ?.let { importDnsMonitorConfigs(it, dryRun, txCtx, lenientIntegrations = true) },
            )
        }
        if (!dryRun) {
            outcomes.forEach { rescheduleChecksFor(it.result.monitorType) }
        }

        return outcomes.applyPostCommitEffects(dryRun)
    }

    private fun rescheduleChecksFor(monitorType: MonitorType) {
        when (monitorType) {
            MonitorType.HTTP_SSL, MonitorType.TCP, MonitorType.ICMP, MonitorType.DNS -> {
                checkSchedulers.first { it.monitorType == monitorType }.run {
                    removeAllChecks()
                    initialize()
                }
            }

            MonitorType.PUSH -> Unit
        }
    }

    /**
     * Applies the effects that must not happen before the changes are committed: announcing the affected monitors to
     * the internal subscribers - the metrics exporters rebuild their meters from these events - and dropping the
     * status page caches that were built from the previous state.
     * A dry run rolls everything back, so it has nothing to announce.
     */
    private fun List<ImportOutcome>.applyPostCommitEffects(dryRun: Boolean): List<MonitorTypeImportResult> {
        if (!dryRun) {
            val events = flatMap { it.lifecycleEvents }
            events.forEach { eventDispatcher.dispatch(it) }
            if (events.isNotEmpty()) {
                statusPageDataActions.invalidateAllCaches()
            }
        }
        return map { it.result }
    }

    private fun ImportOutcome.applyPostCommitEffects(dryRun: Boolean): MonitorTypeImportResult =
        listOf(this).applyPostCommitEffects(dryRun).single()

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
    }.applyPostCommitEffects(dryRun)

    fun importPushMonitorConfigs(
        monitorConfigs: List<PushMonitorCreator>,
        dryRun: Boolean,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        importPushMonitorConfigs(monitorConfigs, dryRun, config.dsl(), lenientIntegrations = false)
    }.applyPostCommitEffects(dryRun)

    fun importIcmpMonitorConfigs(
        monitorConfigs: List<IcmpMonitorCreator>,
        dryRun: Boolean,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        importIcmpMonitorConfigs(monitorConfigs, dryRun, config.dsl(), lenientIntegrations = false)
    }.applyPostCommitEffects(dryRun)

    fun importTcpMonitorConfigs(
        monitorConfigs: List<TcpMonitorCreator>,
        dryRun: Boolean,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        importTcpMonitorConfigs(monitorConfigs, dryRun, config.dsl(), lenientIntegrations = false)
    }.applyPostCommitEffects(dryRun)

    fun importDnsMonitorConfigs(
        monitorConfigs: List<DnsMonitorCreator>,
        dryRun: Boolean,
    ): MonitorTypeImportResult = dslContext.transactionResult { config ->
        importDnsMonitorConfigs(monitorConfigs, dryRun, config.dsl(), lenientIntegrations = false)
    }.applyPostCommitEffects(dryRun)

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
    ): ImportOutcome {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            val existing = httpMonitorRepository.findByName(importedMonitor.name, txCtx)
            httpMonitorRepository.upsert(importedMonitor.toMonitorRecord(resolved.valid), txCtx).also { upserted ->
                // If the latency history is disabled, we need to delete all the existing logs
                if (existing != null && existing.latencyHistoryEnabled && !upserted.latencyHistoryEnabled) {
                    latencyLogRepository.deleteAllByMonitorId(upserted.id, txCtx)
                }
            }
        }
        logger.info("Loaded ${monitorConfigs.size} HTTP monitors from external config, dryrun: $dryRun")

        val deleted = httpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info("Deleted ${deleted.size} HTTP monitors that were not in the external config, dryrun: $dryRun")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return ImportOutcome(
            result = MonitorTypeImportResult(
                monitorType = MonitorType.HTTP_SSL,
                receivedCnt = monitorConfigs.size,
                imported = upsertedMonitors.map { it.monitorId() },
                deleted = deleted.map { it.monitorId },
                ignoredIntegrations = ignoredIntegrations.toList(),
            ),
            lifecycleEvents = upsertedMonitors.map { MonitorUpdateEvent(it.numericMonitorId()) } +
                deleted.map { MonitorDeleteEvent(it.numericMonitorId) },
        )
    }

    private fun importPushMonitorConfigs(
        monitorConfigs: List<PushMonitorCreator>,
        dryRun: Boolean,
        txCtx: DSLContext,
        lenientIntegrations: Boolean,
    ): ImportOutcome {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            val existing = pushMonitorRepository.findByName(importedMonitor.name, txCtx)
            pushMonitorRepository.upsert(importedMonitor.toMonitorRecord(resolved.valid), txCtx).also { upserted ->
                // The already recorded failures were counted against the previous settings of the monitor, so
                // they are not comparable to the updated ones anymore
                if (existing != null && upserted.affectsFailureCounting(existing)) {
                    pendingFailureRepository.deleteByMonitorId(upserted.id, txCtx)
                }
            }
        }
        logger.info("Loaded ${monitorConfigs.size} push monitors from external config, dryrun: $dryRun")

        val deleted = pushMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info("Deleted ${deleted.size} push monitors that were not in the external config, dryrun: $dryRun")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return ImportOutcome(
            result = MonitorTypeImportResult(
                monitorType = MonitorType.PUSH,
                receivedCnt = monitorConfigs.size,
                imported = upsertedMonitors.map { it.monitorId() },
                deleted = deleted.map { it.monitorId },
                ignoredIntegrations = ignoredIntegrations.toList(),
            ),
            lifecycleEvents = upsertedMonitors.map { MonitorUpdateEvent(it.numericMonitorId()) } +
                deleted.map { MonitorDeleteEvent(it.numericMonitorId) },
        )
    }

    private fun importIcmpMonitorConfigs(
        monitorConfigs: List<IcmpMonitorCreator>,
        dryRun: Boolean,
        txCtx: DSLContext,
        lenientIntegrations: Boolean,
    ): ImportOutcome {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            val existing = icmpMonitorRepository.findByName(importedMonitor.name, txCtx)
            icmpMonitorRepository.upsert(importedMonitor.toMonitorRecord(resolved.valid), txCtx).also { upserted ->
                // If the metrics history is disabled, we need to delete all the existing logs
                if (existing != null && existing.metricsHistoryEnabled && !upserted.metricsHistoryEnabled) {
                    icmpMetricsLogRepository.deleteAllByMonitorId(upserted.id, txCtx)
                }
            }
        }
        logger.info("Loaded ${monitorConfigs.size} ICMP monitors from external config, dryrun: $dryRun")

        val deleted = icmpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info("Deleted ${deleted.size} ICMP monitors that were not in the external config, dryrun: $dryRun")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return ImportOutcome(
            result = MonitorTypeImportResult(
                monitorType = MonitorType.ICMP,
                receivedCnt = monitorConfigs.size,
                imported = upsertedMonitors.map { it.monitorId() },
                deleted = deleted.map { it.monitorId },
                ignoredIntegrations = ignoredIntegrations.toList(),
            ),
            lifecycleEvents = upsertedMonitors.map { MonitorUpdateEvent(it.numericMonitorId()) } +
                deleted.map { MonitorDeleteEvent(it.numericMonitorId) },
        )
    }

    private fun importTcpMonitorConfigs(
        monitorConfigs: List<TcpMonitorCreator>,
        dryRun: Boolean,
        txCtx: DSLContext,
        lenientIntegrations: Boolean,
    ): ImportOutcome {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            val existing = tcpMonitorRepository.findByName(importedMonitor.name, txCtx)
            tcpMonitorRepository.upsert(importedMonitor.toMonitorRecord(resolved.valid), txCtx).also { upserted ->
                // If the metrics history is disabled, we need to delete all the existing logs
                if (existing != null && existing.metricsHistoryEnabled && !upserted.metricsHistoryEnabled) {
                    tcpMetricsLogRepository.deleteAllByMonitorId(upserted.id, txCtx)
                }
            }
        }
        logger.info("Loaded ${monitorConfigs.size} TCP monitors from external config, dryrun: $dryRun")

        val deleted = tcpMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info("Deleted ${deleted.size} TCP monitors that were not in the external config, dryrun: $dryRun")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return ImportOutcome(
            result = MonitorTypeImportResult(
                monitorType = MonitorType.TCP,
                receivedCnt = monitorConfigs.size,
                imported = upsertedMonitors.map { it.monitorId() },
                deleted = deleted.map { it.monitorId },
                ignoredIntegrations = ignoredIntegrations.toList(),
            ),
            lifecycleEvents = upsertedMonitors.map { MonitorUpdateEvent(it.numericMonitorId()) } +
                deleted.map { MonitorDeleteEvent(it.numericMonitorId) },
        )
    }

    private fun importDnsMonitorConfigs(
        monitorConfigs: List<DnsMonitorCreator>,
        dryRun: Boolean,
        txCtx: DSLContext,
        lenientIntegrations: Boolean,
    ): ImportOutcome {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            val existing = dnsMonitorRepository.findByName(importedMonitor.name, txCtx)
            dnsMonitorRepository.upsert(importedMonitor.toMonitorRecord(resolved.valid), txCtx).also { upserted ->
                // If the metrics history is disabled, we need to delete all the existing logs
                if (existing != null && existing.metricsHistoryEnabled && !upserted.metricsHistoryEnabled) {
                    dnsMetricsLogRepository.deleteAllByMonitorId(upserted.id, txCtx)
                }
            }
        }
        logger.info("Loaded ${monitorConfigs.size} DNS monitors from external config, dryrun: $dryRun")

        val deleted = dnsMonitorRepository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info("Deleted ${deleted.size} DNS monitors that were not in the external config, dryrun: $dryRun")
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return ImportOutcome(
            result = MonitorTypeImportResult(
                monitorType = MonitorType.DNS,
                receivedCnt = monitorConfigs.size,
                imported = upsertedMonitors.map { it.monitorId() },
                deleted = deleted.map { it.monitorId },
                ignoredIntegrations = ignoredIntegrations.toList(),
            ),
            lifecycleEvents = upsertedMonitors.map { MonitorUpdateEvent(it.numericMonitorId()) } +
                deleted.map { MonitorDeleteEvent(it.numericMonitorId) },
        )
    }
}
