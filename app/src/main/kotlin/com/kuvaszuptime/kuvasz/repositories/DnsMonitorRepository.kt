package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.JsonNodeToMatcherListConverter
import com.kuvaszuptime.kuvasz.jooq.Keys.UNIQUE_DNS_MONITOR_NAME
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DnsMonitor.DNS_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DnsUptimeEvent.DNS_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.monitorId
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.SortField
import org.jooq.Table
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL

@Singleton
@Suppress("TooManyFunctions")
class DnsMonitorRepository(private val dslContext: DSLContext) : MonitorRepository<DnsMonitorRecord> {

    private val matcherListConverter = JsonNodeToMatcherListConverter()

    override fun findById(monitorId: Long, txCtx: DSLContext?): DnsMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(DNS_MONITOR)
        .where(DNS_MONITOR.ID.eq(monitorId))
        .fetchOne()

    fun findByName(name: String): DnsMonitorRecord? = dslContext
        .selectFrom(DNS_MONITOR)
        .where(DNS_MONITOR.NAME.eq(name))
        .fetchOne()

    fun fetchAll(): List<DnsMonitorRecord> = dslContext
        .selectFrom(DNS_MONITOR)
        .fetch()

    fun fetchByEnabled(enabled: Boolean): List<DnsMonitorRecord> = dslContext
        .selectFrom(DNS_MONITOR)
        .where(DNS_MONITOR.ENABLED.eq(enabled))
        .fetch()

    override fun deleteById(monitorId: Long, txCtx: DSLContext?): Int = (txCtx ?: dslContext)
        .deleteFrom(DNS_MONITOR)
        .where(DNS_MONITOR.ID.eq(monitorId))
        .execute()

    fun returningInsert(monitor: DnsMonitorRecord): DnsMonitorRecord =
        try {
            dslContext
                .insertInto(DNS_MONITOR)
                .set(monitor)
                .returning(DNS_MONITOR.asterisk())
                .fetchOneOrThrow<DnsMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun updateIntegrations(monitorId: Long, newIntegrations: Array<IntegrationID>) {
        dslContext
            .update(DNS_MONITOR)
            .set(DNS_MONITOR.INTEGRATIONS, newIntegrations)
            .where(DNS_MONITOR.ID.eq(monitorId))
            .execute()
    }

    @Suppress("IgnoredReturnValue", "UnsafeCallOnNullableType")
    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
        monitorNames: List<String>? = null,
    ): List<DnsMonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                enabled?.let { and(DNS_MONITOR.ENABLED.eq(it)) }
                uptimeStatus.takeIf { it.isNotEmpty() }?.let {
                    and(latestUptimeEventSelect.field(DNS_UPTIME_EVENT.STATUS)!!.`in`(it))
                }
                monitorNames?.let { and(DNS_MONITOR.NAME.`in`(it)) }
                sortedBy?.let { orderBy(it) }
            }
            .fetchInto(DnsMonitorDetailsDto::class.java)

    fun getMonitorWithDetails(monitorId: Long): DnsMonitorDetailsDto? =
        monitorDetailsSelect()
            .and(DNS_MONITOR.ID.eq(monitorId))
            .fetchOneInto(DnsMonitorDetailsDto::class.java)

    fun returningUpdate(
        updatedMonitor: DnsMonitorRecord,
        txCtx: DSLContext = dslContext,
    ): DnsMonitorRecord =
        try {
            txCtx
                .update(DNS_MONITOR)
                .set(DNS_MONITOR.NAME, updatedMonitor.name)
                .set(DNS_MONITOR.HOST, updatedMonitor.host)
                .set(DNS_MONITOR.RESOLVER_HOST, updatedMonitor.resolverHost)
                .set(DNS_MONITOR.RESOLVER_PORT, updatedMonitor.resolverPort)
                .set(DNS_MONITOR.TRANSPORT, updatedMonitor.transport)
                .set(DNS_MONITOR.RECORD_MATCHERS, updatedMonitor.recordMatchers)
                .set(DNS_MONITOR.EXPECTED_RESPONSE_CODE, updatedMonitor.expectedResponseCode)
                .set(DNS_MONITOR.DRIFT_DETECTION_ENABLED, updatedMonitor.driftDetectionEnabled)
                .set(DNS_MONITOR.DRIFT_RECORD_TYPES, updatedMonitor.driftRecordTypes)
                .set(DNS_MONITOR.UPTIME_CHECK_INTERVAL, updatedMonitor.uptimeCheckInterval)
                .set(DNS_MONITOR.TIMEOUT_MS, updatedMonitor.timeoutMs)
                .set(DNS_MONITOR.LATENCY_THRESHOLD_MS, updatedMonitor.latencyThresholdMs)
                .set(DNS_MONITOR.FAILURE_COUNT_THRESHOLD, updatedMonitor.failureCountThreshold)
                .set(DNS_MONITOR.ENABLED, updatedMonitor.enabled)
                .set(DNS_MONITOR.INTEGRATIONS, updatedMonitor.integrations)
                .set(DNS_MONITOR.METRICS_HISTORY_ENABLED, updatedMonitor.metricsHistoryEnabled)
                .set(DNS_MONITOR.UPDATED_AT, getCurrentTimestamp())
                .where(DNS_MONITOR.ID.eq(updatedMonitor.id))
                .returning(DNS_MONITOR.asterisk())
                .fetchOneOrThrow<DnsMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun upsert(monitor: DnsMonitorRecord, txCtx: DSLContext = this.dslContext): DnsMonitorRecord = txCtx
        .insertInto(DNS_MONITOR)
        .set(monitor)
        .onConflictOnConstraint(UNIQUE_DNS_MONITOR_NAME)
        .doUpdate()
        .setNonKeyToExcluded()
        .set(DNS_MONITOR.UPDATED_AT, getCurrentTimestamp())
        .returning(DNS_MONITOR.asterisk())
        .fetchOneOrThrow()

    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext = this.dslContext): List<MonitorID> = txCtx
        .deleteFrom(DNS_MONITOR)
        .where(DNS_MONITOR.ID.notIn(ignoredIds))
        .returning(DNS_MONITOR.NAME)
        .fetch()
        .map { it.monitorId() }

    val latestUptimeEventSelect: Table<DnsUptimeEventRecord?> = DSL.table(
        DSL.selectFrom(DNS_UPTIME_EVENT)
            .where(DNS_UPTIME_EVENT.MONITOR_ID.eq(DNS_MONITOR.ID))
            .and(DNS_UPTIME_EVENT.ENDED_AT.isNull)
            .orderBy(DNS_UPTIME_EVENT.UPDATED_AT.desc())
            .limit(1)
    )

    @Suppress("LongMethod", "UnsafeCallOnNullableType")
    private fun monitorDetailsSelect(): SelectConditionStep<out Record?> = dslContext
        .select(
            DNS_MONITOR.ID.`as`(DnsMonitorDetailsDto::id.name),
            DNS_MONITOR.NAME.`as`(DnsMonitorDetailsDto::name.name),
            DNS_MONITOR.HOST.`as`(DnsMonitorDetailsDto::host.name),
            DNS_MONITOR.RESOLVER_HOST.`as`(DnsMonitorDetailsDto::resolverHost.name),
            DNS_MONITOR.RESOLVER_PORT.`as`(DnsMonitorDetailsDto::resolverPort.name),
            DNS_MONITOR.TRANSPORT.`as`(DnsMonitorDetailsDto::transport.name),
            DNS_MONITOR.RECORD_MATCHERS.`as`(DnsMonitorDetailsDto::recordMatchers.name).convert(matcherListConverter),
            DNS_MONITOR.EXPECTED_RESPONSE_CODE.`as`(DnsMonitorDetailsDto::expectedResponseCode.name),
            DNS_MONITOR.DRIFT_DETECTION_ENABLED.`as`(DnsMonitorDetailsDto::driftDetectionEnabled.name),
            DNS_MONITOR.DRIFT_RECORD_TYPES.`as`(DnsMonitorDetailsDto::driftRecordTypes.name),
            DNS_MONITOR.UPTIME_CHECK_INTERVAL.`as`(DnsMonitorDetailsDto::uptimeCheckInterval.name),
            DNS_MONITOR.TIMEOUT_MS.`as`(DnsMonitorDetailsDto::timeoutMs.name),
            DNS_MONITOR.LATENCY_THRESHOLD_MS.`as`(DnsMonitorDetailsDto::latencyThresholdMs.name),
            DNS_MONITOR.FAILURE_COUNT_THRESHOLD.`as`(DnsMonitorDetailsDto::failureCountThreshold.name),
            DNS_MONITOR.METRICS_HISTORY_ENABLED.`as`(DnsMonitorDetailsDto::metricsHistoryEnabled.name),
            DNS_MONITOR.ENABLED.`as`(DnsMonitorDetailsDto::enabled.name),
            DNS_MONITOR.CREATED_AT.`as`(DnsMonitorDetailsDto::createdAt.name),
            DNS_MONITOR.UPDATED_AT.`as`(DnsMonitorDetailsDto::updatedAt.name),
            latestUptimeEventSelect.field(DNS_UPTIME_EVENT.STATUS)!!.`as`(DnsMonitorDetailsDto::uptimeStatus.name),
            latestUptimeEventSelect.field(DNS_UPTIME_EVENT.STARTED_AT)!!
                .`as`(DnsMonitorDetailsDto::uptimeStatusStartedAt.name),
            latestUptimeEventSelect.field(DNS_UPTIME_EVENT.UPDATED_AT)!!
                .`as`(DnsMonitorDetailsDto::lastUptimeCheck.name),
            latestUptimeEventSelect.field(DNS_UPTIME_EVENT.ERROR)!!.`as`(DnsMonitorDetailsDto::uptimeError.name),
            DSL.array(arrayOf<String>()).`as`(DnsMonitorDetailsDto::effectiveIntegrations.name),
            DNS_MONITOR.INTEGRATIONS.`as`(DnsMonitorDetailsDto::integrations.name),
            DSL.coalesce(statusPagesSubselect.field("slugs"), DSL.array(arrayOf<String>()))
                .`as`(DnsMonitorDetailsDto::statusPages.name),
            // Placeholders for fields populated by the actions layer, not by SQL
            DSL.array(arrayOf<String>()).`as`(DnsMonitorDetailsDto::maintenanceWindows.name),
            DSL.inline(false).`as`(DnsMonitorDetailsDto::inMaintenance.name),
        )
        .from(DNS_MONITOR)
        .leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        .leftJoin(statusPagesSubselect)
        .on(
            monitorNameField
                .eq(
                    DSL.`val`(MonitorType.DNS.identifier)
                        .concat(":")
                        .concat(DNS_MONITOR.NAME)
                )
        )
        .where(DSL.trueCondition())
}
