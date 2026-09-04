package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.metrics.numericMonitorId
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorTypeImportResult
import com.kuvaszuptime.kuvasz.models.events.MonitorDeleteEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.tcp.TcpMonitorCreator
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.MonitorCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.dns.DnsMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageCacheInvalidator
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import com.kuvaszuptime.kuvasz.validation.ResolvedIntegrationIds
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class MonitorImporter(
    private val integrationIdValidator: IntegrationIdValidator,
    private val httpMonitors: HttpMonitorTypeSupport,
    private val pushMonitors: PushMonitorTypeSupport,
    private val icmpMonitors: IcmpMonitorTypeSupport,
    private val tcpMonitors: TcpMonitorTypeSupport,
    private val dnsMonitors: DnsMonitorTypeSupport,
    private val dslContext: DSLContext,
    private val checkSchedulers: List<MonitorCheckScheduler>,
    private val eventDispatcher: EventDispatcher,
    private val statusPageCacheInvalidator: StatusPageCacheInvalidator,
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
                httpMonitorConfigs.doIfNotEmpty { nonEmptyConfigs ->
                    httpMonitors.reconcile(nonEmptyConfigs, dryRun, txCtx, lenientIntegrations = true)
                },
                pushMonitorConfigs.doIfNotEmpty { nonEmptyConfigs ->
                    pushMonitors.reconcile(nonEmptyConfigs, dryRun, txCtx, lenientIntegrations = true)
                },
                icmpMonitorConfigs.doIfNotEmpty { nonEmptyConfigs ->
                    icmpMonitors.reconcile(nonEmptyConfigs, dryRun, txCtx, lenientIntegrations = true)
                },
                tcpMonitorConfigs.doIfNotEmpty { nonEmptyConfigs ->
                    tcpMonitors.reconcile(nonEmptyConfigs, dryRun, txCtx, lenientIntegrations = true)
                },
                dnsMonitorConfigs.doIfNotEmpty { nonEmptyConfigs ->
                    dnsMonitors.reconcile(nonEmptyConfigs, dryRun, txCtx, lenientIntegrations = true)
                },
            )
        }
        if (!dryRun) {
            outcomes.forEach { rescheduleChecksFor(it.result.monitorType) }
        }

        return outcomes.applyPostCommitEffects(dryRun)
    }

    private fun <T : Any, R : Any> List<T>.doIfNotEmpty(action: (List<T>) -> R): R? =
        takeIf { it.isNotEmpty() }?.let { action(it) }

    /**
     * Entry points for loading monitors from the startup YAML config. Integration references are validated strictly:
     * a non-existing integration fails the import, so a misconfigured YAML is caught at startup instead of silently
     * dropping references.
     */
    fun importHttpMonitorConfigs(monitorConfigs: List<HttpMonitorCreator>, dryRun: Boolean) =
        httpMonitors.importFromConfig(monitorConfigs, dryRun)

    fun importPushMonitorConfigs(monitorConfigs: List<PushMonitorCreator>, dryRun: Boolean) =
        pushMonitors.importFromConfig(monitorConfigs, dryRun)

    fun importIcmpMonitorConfigs(monitorConfigs: List<IcmpMonitorCreator>, dryRun: Boolean) =
        icmpMonitors.importFromConfig(monitorConfigs, dryRun)

    fun importTcpMonitorConfigs(monitorConfigs: List<TcpMonitorCreator>, dryRun: Boolean) =
        tcpMonitors.importFromConfig(monitorConfigs, dryRun)

    fun importDnsMonitorConfigs(monitorConfigs: List<DnsMonitorCreator>, dryRun: Boolean) =
        dnsMonitors.importFromConfig(monitorConfigs, dryRun)

    private fun <C : MonitorCreator<R>, R : MonitorRecord> MonitorTypeSupport<C, R, *>.importFromConfig(
        monitorConfigs: List<C>,
        dryRun: Boolean,
    ): MonitorTypeImportResult = dslContext
        .transactionResult { config ->
            reconcile(monitorConfigs, dryRun, config.dsl(), lenientIntegrations = false)
        }
        .applyPostCommitEffects(dryRun)

    /**
     * Reconciles the monitors of a single type against the given configs: every config is upserted by its name, and
     * everything that is not among them is deleted. A dry run rolls the whole thing back, but still reports what it
     * would have done.
     *
     * An empty config list is a valid input that deletes every monitor of the type - that is how an explicitly empty
     * YAML list is meant to behave. Skipping a type that was not part of an import at all is the caller's job.
     */
    private fun <C : MonitorCreator<R>, R : MonitorRecord> MonitorTypeSupport<C, R, *>.reconcile(
        monitorConfigs: List<C>,
        dryRun: Boolean,
        txCtx: DSLContext,
        lenientIntegrations: Boolean,
    ): ImportOutcome {
        val ignoredIntegrations = mutableSetOf<String>()
        val upsertedMonitors = monitorConfigs.map { importedMonitor ->
            val resolved = resolveIntegrations(importedMonitor.integrations.orEmpty(), lenientIntegrations)
            ignoredIntegrations.addAll(resolved.ignored)
            val toUpsert = importedMonitor.toMonitorRecord(resolved.valid)
            val previous = repository.findByName(toUpsert.name, txCtx)
            repository.upsert(toUpsert, txCtx).also { upserted -> onUpserted(previous, upserted, txCtx) }
        }
        logger.info(
            "Loaded ${monitorConfigs.size} ${monitorType.identifier} monitors from external config, " +
                "dryrun: $dryRun"
        )

        val deleted = repository.deleteAllExcept(ignoredIds = upsertedMonitors.map { it.id }, txCtx)
        if (deleted.isNotEmpty()) {
            logger.info(
                "Deleted ${deleted.size} ${monitorType.identifier} monitors that were not in the external config, " +
                    "dryrun: $dryRun"
            )
        }

        if (dryRun) txCtx.connection { it.rollback() }

        return ImportOutcome(
            result = MonitorTypeImportResult(
                monitorType = monitorType,
                receivedCnt = monitorConfigs.size,
                imported = upsertedMonitors.map { MonitorID(monitorType, it.name) },
                deleted = deleted.map { it.monitorId },
                ignoredIntegrations = ignoredIntegrations.toList(),
            ),
            lifecycleEvents = upsertedMonitors.map { MonitorUpdateEvent(it.numericMonitorId()) } +
                deleted.map { MonitorDeleteEvent(it.numericMonitorId) },
        )
    }

    private fun resolveIntegrations(rawIds: List<String>, lenient: Boolean): ResolvedIntegrationIds =
        if (lenient) {
            integrationIdValidator.resolveIntegrationIds(rawIds)
        } else {
            ResolvedIntegrationIds(valid = integrationIdValidator.validateIntegrationIds(rawIds), ignored = emptyList())
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
                statusPageCacheInvalidator.invalidateAllCaches()
            }
        }
        return map { it.result }
    }

    private fun ImportOutcome.applyPostCommitEffects(dryRun: Boolean): MonitorTypeImportResult =
        listOf(this).applyPostCommitEffects(dryRun).single()
}
