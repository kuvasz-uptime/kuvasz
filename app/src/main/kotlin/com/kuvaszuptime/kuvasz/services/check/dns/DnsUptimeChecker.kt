package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.dns.assertionRecordTypes
import com.kuvaszuptime.kuvasz.models.monitor.dns.driftWatchTypes
import com.kuvaszuptime.kuvasz.models.monitor.dns.recordMatchersAsList
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsResolutionSnapshotRepository
import com.kuvaszuptime.kuvasz.repositories.DnsUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.isDownNow
import com.kuvaszuptime.kuvasz.util.loggerFor
import jakarta.inject.Singleton

@Singleton
class DnsUptimeChecker(
    private val resolveExecutor: DnsResolveExecutor,
    private val uptimeEventRepository: DnsUptimeEventRepository,
    private val metricsLogRepository: DnsMetricsLogRepository,
    private val databaseEventHandler: DatabaseEventHandler,
    private val eventDispatcher: EventDispatcher,
    private val pendingFailureRepository: PendingFailureRepository,
    private val monitorRepository: DnsMonitorRepository,
    private val snapshotRepository: DnsResolutionSnapshotRepository,
) {
    fun check(
        monitor: DnsMonitorRecord,
        doAfter: ((monitor: DnsMonitorRecord) -> Unit)? = null,
    ) {
        logger.debug("Starting DNS check for monitor [${monitor.name}] on ${monitor.host}")

        val matchers = monitor.recordMatchersAsList()
        val assertionTypes = matchers.assertionRecordTypes()
        val driftWatchTypes = monitor.driftWatchTypes(default = assertionTypes)
        val checkResult = resolveExecutor.execute(
            host = monitor.host,
            recordTypes = assertionTypes,
            resolverHost = monitor.resolverHost,
            resolverPort = monitor.resolverPort,
            transport = monitor.transport,
            timeoutMs = monitor.timeoutMs,
            driftRecordTypes = driftWatchTypes,
        )

        if (monitor.metricsHistoryEnabled) {
            metricsLogRepository.insertLog(
                monitorId = monitor.id,
                latencyMs = checkResult.latencyMs,
            )
        }

        checkResult.evaluate(monitor, matchers)

        // Drift is evaluated independently of UP/DOWN: a monitor can be UP and still have its answer set change. It is
        // only checked when every watched type produced a trustworthy answer, so that a failed lookup or a server-side
        // error -- both of which yield an empty answer section -- is never mistaken for records having been removed.
        if (driftWatchTypes.isNotEmpty() && checkResult.isTrustworthyForDrift()) {
            detectDrift(monitor, checkResult.records.filterKeys { it in driftWatchTypes })
        }

        logger.debug("DNS uptime check for monitor [${monitor.name}] finished")
        if (doAfter != null) {
            monitorRepository.findById(monitor.id, null)?.let { upToDateMonitor ->
                logger.debug("Calling doAfter() hook on monitor with name [${upToDateMonitor.name}]")
                doAfter(upToDateMonitor)
            }
        }
    }

    private fun DnsCheckResult.evaluate(monitor: DnsMonitorRecord, matchers: List<DnsRecordMatcher>) {
        val previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
        val downReason = determineDownReason(monitor, matchers)

        if (downReason != null) {
            val event = DnsMonitorDownEvent(
                monitor = monitor,
                error = downReason,
                previousEvent = previousEvent,
                latencyInMs = latencyMs,
            )
            if (event.isDownNow(pendingFailureRepository)) {
                databaseEventHandler.handleUptimeMonitorEvent(event)
                eventDispatcher.dispatch(event)
            }
        } else {
            val event = DnsMonitorUpEvent(
                monitor = monitor,
                previousEvent = previousEvent,
                latencyInMs = latencyMs,
            )
            pendingFailureRepository.deleteByMonitorId(monitor.id)
            databaseEventHandler.handleUptimeMonitorEvent(event)
            eventDispatcher.dispatch(event)
        }
    }

    /**
     * Returns a human-readable reason when the check should be considered DOWN, or null when it is UP. The checks are
     * evaluated in order of severity: a hard resolution failure first, then the response code, then the record
     * assertions, and finally the latency threshold.
     */
    private fun DnsCheckResult.determineDownReason(
        monitor: DnsMonitorRecord,
        matchers: List<DnsRecordMatcher>,
    ): String? {
        if (error != null) return error

        val matchResult = DnsRecordNormalizer.evaluate(matchers, records)
        val latencyThreshold = monitor.latencyThresholdMs
        val latencyExceeded = latencyThreshold != null && latencyMs != null && latencyMs > latencyThreshold

        return when {
            responseCode != monitor.expectedResponseCode ->
                Messages.dnsResponseCodeError(responseCode.toString(), monitor.expectedResponseCode.toString())

            !matchResult.matched ->
                Messages.dnsRecordMatcherError(matchResult.failedMatchers.describe())

            latencyExceeded ->
                Messages.dnsLatencyThresholdError(latencyMs.toString(), latencyThreshold.toString())

            else -> null
        }
    }

    /**
     * Whether the answer set is a reliable statement about the monitored name's records. Anything other than a
     * complete NOERROR resolution (a transport failure, or an NXDOMAIN/SERVFAIL/REFUSED response) returns empty answer
     * sections that would otherwise look like every watched record having been removed at once.
     */
    private fun DnsCheckResult.isTrustworthyForDrift(): Boolean =
        error == null && driftRecordsComplete && responseCode == DnsResponseCode.NOERROR

    /**
     * Compares the freshly resolved answer set against the stored snapshot. On the very first successful check the
     * snapshot is seeded silently, afterward a change emits a single [DnsRecordsChangedEvent] and re-seeds it.
     */
    private fun detectDrift(monitor: DnsMonitorRecord, currentRecords: Map<DnsRecordType, List<String>>) {
        val previousRecords = snapshotRepository.getRecords(monitor.id)
        when {
            previousRecords == null -> snapshotRepository.upsert(monitor.id, currentRecords)
            previousRecords != currentRecords -> {
                eventDispatcher.dispatch(DnsRecordsChangedEvent(monitor, previousRecords, currentRecords))
                snapshotRepository.upsert(monitor.id, currentRecords)
            }
        }
    }

    private fun List<DnsRecordMatcher>.describe(): String =
        joinToString(separator = "; ") { "${it.recordType} ${it.matchType} \"${it.value}\"" }

    companion object {
        private val logger = loggerFor<DnsUptimeChecker>()
    }
}
