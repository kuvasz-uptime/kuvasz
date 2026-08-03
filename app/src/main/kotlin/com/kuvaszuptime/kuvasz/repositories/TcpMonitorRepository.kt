package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.UNIQUE_TCP_MONITOR_NAME
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.TcpMonitor.TCP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.TcpUptimeEvent.TCP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.tcp.monitorId
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
class TcpMonitorRepository(private val dslContext: DSLContext) : MonitorRepository<TcpMonitorRecord> {

    override fun findById(monitorId: Long, txCtx: DSLContext?): TcpMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(TCP_MONITOR)
        .where(TCP_MONITOR.ID.eq(monitorId))
        .fetchOne()

    fun findByName(name: String): TcpMonitorRecord? = dslContext
        .selectFrom(TCP_MONITOR)
        .where(TCP_MONITOR.NAME.eq(name))
        .fetchOne()

    fun fetchAll(): List<TcpMonitorRecord> = dslContext
        .selectFrom(TCP_MONITOR)
        .fetch()

    fun fetchByEnabled(enabled: Boolean): List<TcpMonitorRecord> = dslContext
        .selectFrom(TCP_MONITOR)
        .where(TCP_MONITOR.ENABLED.eq(enabled))
        .fetch()

    override fun deleteById(monitorId: Long, txCtx: DSLContext?): Int = (txCtx ?: dslContext)
        .deleteFrom(TCP_MONITOR)
        .where(TCP_MONITOR.ID.eq(monitorId))
        .execute()

    @Suppress("IgnoredReturnValue", "UnsafeCallOnNullableType")
    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
        monitorNames: List<String>? = null,
    ): List<TcpMonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                enabled?.let { and(TCP_MONITOR.ENABLED.eq(it)) }
                uptimeStatus.takeIf { it.isNotEmpty() }?.let {
                    and(latestUptimeEventSelect.field(TCP_UPTIME_EVENT.STATUS)!!.`in`(it))
                }
                monitorNames?.let { and(TCP_MONITOR.NAME.`in`(it)) }
                sortedBy?.let { orderBy(it, TCP_MONITOR.ID.asc()) }
            }
            .fetchInto(TcpMonitorDetailsDto::class.java)

    fun getMonitorWithDetails(monitorId: Long): TcpMonitorDetailsDto? =
        monitorDetailsSelect()
            .and(TCP_MONITOR.ID.eq(monitorId))
            .fetchOneInto(TcpMonitorDetailsDto::class.java)

    fun returningInsert(monitor: TcpMonitorRecord): TcpMonitorRecord =
        try {
            dslContext
                .insertInto(TCP_MONITOR)
                .set(monitor)
                .returning(TCP_MONITOR.asterisk())
                .fetchOneOrThrow<TcpMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun returningUpdate(
        updatedMonitor: TcpMonitorRecord,
        txCtx: DSLContext = dslContext,
    ): TcpMonitorRecord =
        try {
            txCtx
                .update(TCP_MONITOR)
                .set(TCP_MONITOR.NAME, updatedMonitor.name)
                .set(TCP_MONITOR.HOST, updatedMonitor.host)
                .set(TCP_MONITOR.PORT, updatedMonitor.port)
                .set(TCP_MONITOR.UPTIME_CHECK_INTERVAL, updatedMonitor.uptimeCheckInterval)
                .set(TCP_MONITOR.TIMEOUT_MS, updatedMonitor.timeoutMs)
                .set(TCP_MONITOR.LATENCY_THRESHOLD_MS, updatedMonitor.latencyThresholdMs)
                .set(TCP_MONITOR.FAILURE_COUNT_THRESHOLD, updatedMonitor.failureCountThreshold)
                .set(TCP_MONITOR.ENABLED, updatedMonitor.enabled)
                .set(TCP_MONITOR.INTEGRATIONS, updatedMonitor.integrations)
                .set(TCP_MONITOR.METRICS_HISTORY_ENABLED, updatedMonitor.metricsHistoryEnabled)
                .set(TCP_MONITOR.UPDATED_AT, getCurrentTimestamp())
                .where(TCP_MONITOR.ID.eq(updatedMonitor.id))
                .returning(TCP_MONITOR.asterisk())
                .fetchOneOrThrow<TcpMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun upsert(monitor: TcpMonitorRecord, txCtx: DSLContext = this.dslContext): TcpMonitorRecord = txCtx
        .insertInto(TCP_MONITOR)
        .set(monitor)
        .onConflictOnConstraint(UNIQUE_TCP_MONITOR_NAME)
        .doUpdate()
        .setNonKeyToExcluded()
        .set(TCP_MONITOR.UPDATED_AT, getCurrentTimestamp())
        .returning(TCP_MONITOR.asterisk())
        .fetchOneOrThrow()

    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext = this.dslContext): List<MonitorID> = txCtx
        .deleteFrom(TCP_MONITOR)
        .where(TCP_MONITOR.ID.notIn(ignoredIds))
        .returning(TCP_MONITOR.NAME)
        .fetch()
        .map { it.monitorId() }

    fun updateIntegrations(monitorId: Long, newIntegrations: Array<IntegrationID>) {
        dslContext
            .update(TCP_MONITOR)
            .set(TCP_MONITOR.INTEGRATIONS, newIntegrations)
            .where(TCP_MONITOR.ID.eq(monitorId))
            .execute()
    }

    val latestUptimeEventSelect: Table<TcpUptimeEventRecord?> = DSL.table(
        DSL.selectFrom(TCP_UPTIME_EVENT)
            .where(TCP_UPTIME_EVENT.MONITOR_ID.eq(TCP_MONITOR.ID))
            .and(TCP_UPTIME_EVENT.ENDED_AT.isNull)
            .orderBy(TCP_UPTIME_EVENT.UPDATED_AT.desc())
            .limit(1)
    )

    @Suppress("LongMethod", "UnsafeCallOnNullableType")
    private fun monitorDetailsSelect(): SelectConditionStep<out Record?> = dslContext
        .select(
            TCP_MONITOR.ID.`as`(TcpMonitorDetailsDto::id.name),
            TCP_MONITOR.NAME.`as`(TcpMonitorDetailsDto::name.name),
            TCP_MONITOR.HOST.`as`(TcpMonitorDetailsDto::host.name),
            TCP_MONITOR.PORT.`as`(TcpMonitorDetailsDto::port.name),
            TCP_MONITOR.UPTIME_CHECK_INTERVAL.`as`(TcpMonitorDetailsDto::uptimeCheckInterval.name),
            TCP_MONITOR.TIMEOUT_MS.`as`(TcpMonitorDetailsDto::timeoutMs.name),
            TCP_MONITOR.LATENCY_THRESHOLD_MS.`as`(TcpMonitorDetailsDto::latencyThresholdMs.name),
            TCP_MONITOR.FAILURE_COUNT_THRESHOLD.`as`(TcpMonitorDetailsDto::failureCountThreshold.name),
            TCP_MONITOR.METRICS_HISTORY_ENABLED.`as`(TcpMonitorDetailsDto::metricsHistoryEnabled.name),
            TCP_MONITOR.ENABLED.`as`(TcpMonitorDetailsDto::enabled.name),
            TCP_MONITOR.CREATED_AT.`as`(TcpMonitorDetailsDto::createdAt.name),
            TCP_MONITOR.UPDATED_AT.`as`(TcpMonitorDetailsDto::updatedAt.name),
            latestUptimeEventSelect.field(TCP_UPTIME_EVENT.STATUS)!!.`as`(TcpMonitorDetailsDto::uptimeStatus.name),
            latestUptimeEventSelect.field(TCP_UPTIME_EVENT.STARTED_AT)!!
                .`as`(TcpMonitorDetailsDto::uptimeStatusStartedAt.name),
            latestUptimeEventSelect.field(TCP_UPTIME_EVENT.UPDATED_AT)!!
                .`as`(TcpMonitorDetailsDto::lastUptimeCheck.name),
            latestUptimeEventSelect.field(TCP_UPTIME_EVENT.ERROR)!!.`as`(TcpMonitorDetailsDto::uptimeError.name),
            DSL.array(arrayOf<String>()).`as`(TcpMonitorDetailsDto::effectiveIntegrations.name),
            TCP_MONITOR.INTEGRATIONS.`as`(TcpMonitorDetailsDto::integrations.name),
            DSL.coalesce(statusPagesSubselect.field("slugs"), DSL.array(arrayOf<String>()))
                .`as`(TcpMonitorDetailsDto::statusPages.name),
            // Placeholders for fields populated by the actions layer, not by SQL
            DSL.array(arrayOf<String>()).`as`(TcpMonitorDetailsDto::maintenanceWindows.name),
            DSL.inline(false).`as`(TcpMonitorDetailsDto::inMaintenance.name),
        )
        .from(TCP_MONITOR)
        .leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        .leftJoin(statusPagesSubselect)
        .on(
            monitorNameField
                .eq(
                    DSL.`val`(MonitorType.TCP.identifier)
                        .concat(":")
                        .concat(TCP_MONITOR.NAME)
                )
        )
        .where(DSL.trueCondition())
}
