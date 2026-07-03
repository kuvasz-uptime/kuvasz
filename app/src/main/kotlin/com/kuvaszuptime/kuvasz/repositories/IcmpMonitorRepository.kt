package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.UNIQUE_ICMP_MONITOR_NAME
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMonitor.ICMP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpUptimeEvent.ICMP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
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
class IcmpMonitorRepository(private val dslContext: DSLContext) : MonitorRepository<IcmpMonitorRecord> {

    override fun findById(monitorId: Long, txCtx: DSLContext?): IcmpMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(ICMP_MONITOR)
        .where(ICMP_MONITOR.ID.eq(monitorId))
        .fetchOne()

    fun findByName(name: String): IcmpMonitorRecord? = dslContext
        .selectFrom(ICMP_MONITOR)
        .where(ICMP_MONITOR.NAME.eq(name))
        .fetchOne()

    fun fetchAll(): List<IcmpMonitorRecord> = dslContext
        .selectFrom(ICMP_MONITOR)
        .fetch()

    fun fetchByEnabled(enabled: Boolean): List<IcmpMonitorRecord> = dslContext
        .selectFrom(ICMP_MONITOR)
        .where(ICMP_MONITOR.ENABLED.eq(enabled))
        .fetch()

    override fun deleteById(monitorId: Long, txCtx: DSLContext?): Int = (txCtx ?: dslContext)
        .deleteFrom(ICMP_MONITOR)
        .where(ICMP_MONITOR.ID.eq(monitorId))
        .execute()

    @Suppress("IgnoredReturnValue", "UnsafeCallOnNullableType")
    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
        monitorNames: List<String>? = null,
    ): List<IcmpMonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                enabled?.let { and(ICMP_MONITOR.ENABLED.eq(it)) }
                uptimeStatus.takeIf { it.isNotEmpty() }?.let {
                    and(latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.STATUS)!!.`in`(it))
                }
                monitorNames?.let { and(ICMP_MONITOR.NAME.`in`(it)) }
                sortedBy?.let { orderBy(it) }
            }
            .fetchInto(IcmpMonitorDetailsDto::class.java)

    fun getMonitorWithDetails(monitorId: Long): IcmpMonitorDetailsDto? =
        monitorDetailsSelect()
            .and(ICMP_MONITOR.ID.eq(monitorId))
            .fetchOneInto(IcmpMonitorDetailsDto::class.java)

    fun returningInsert(monitor: IcmpMonitorRecord): IcmpMonitorRecord =
        try {
            dslContext
                .insertInto(ICMP_MONITOR)
                .set(monitor)
                .returning(ICMP_MONITOR.asterisk())
                .fetchOneOrThrow<IcmpMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun returningUpdate(
        updatedMonitor: IcmpMonitorRecord,
        txCtx: DSLContext = dslContext,
    ): IcmpMonitorRecord =
        try {
            txCtx
                .update(ICMP_MONITOR)
                .set(ICMP_MONITOR.NAME, updatedMonitor.name)
                .set(ICMP_MONITOR.HOST, updatedMonitor.host)
                .set(ICMP_MONITOR.UPTIME_CHECK_INTERVAL, updatedMonitor.uptimeCheckInterval)
                .set(ICMP_MONITOR.PACKET_COUNT, updatedMonitor.packetCount)
                .set(ICMP_MONITOR.TIMEOUT_SECONDS, updatedMonitor.timeoutSeconds)
                .set(ICMP_MONITOR.PACKET_LOSS_THRESHOLD, updatedMonitor.packetLossThreshold)
                .set(ICMP_MONITOR.FAILURE_COUNT_THRESHOLD, updatedMonitor.failureCountThreshold)
                .set(ICMP_MONITOR.ENABLED, updatedMonitor.enabled)
                .set(ICMP_MONITOR.INTEGRATIONS, updatedMonitor.integrations)
                .set(ICMP_MONITOR.METRICS_HISTORY_ENABLED, updatedMonitor.metricsHistoryEnabled)
                .set(ICMP_MONITOR.UPDATED_AT, getCurrentTimestamp())
                .where(ICMP_MONITOR.ID.eq(updatedMonitor.id))
                .returning(ICMP_MONITOR.asterisk())
                .fetchOneOrThrow<IcmpMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun upsert(monitor: IcmpMonitorRecord, txCtx: DSLContext = this.dslContext): IcmpMonitorRecord = txCtx
        .insertInto(ICMP_MONITOR)
        .set(monitor)
        .onConflictOnConstraint(UNIQUE_ICMP_MONITOR_NAME)
        .doUpdate()
        .setNonKeyToExcluded()
        .set(ICMP_MONITOR.UPDATED_AT, getCurrentTimestamp())
        .returning(ICMP_MONITOR.asterisk())
        .fetchOneOrThrow()

    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext = this.dslContext): Int = txCtx
        .deleteFrom(ICMP_MONITOR)
        .where(ICMP_MONITOR.ID.notIn(ignoredIds))
        .execute()

    fun updateIntegrations(monitorId: Long, newIntegrations: Array<IntegrationID>) {
        dslContext
            .update(ICMP_MONITOR)
            .set(ICMP_MONITOR.INTEGRATIONS, newIntegrations)
            .where(ICMP_MONITOR.ID.eq(monitorId))
            .execute()
    }

    val latestUptimeEventSelect: Table<IcmpUptimeEventRecord?> = DSL.table(
        DSL.selectFrom(ICMP_UPTIME_EVENT)
            .where(ICMP_UPTIME_EVENT.MONITOR_ID.eq(ICMP_MONITOR.ID))
            .and(ICMP_UPTIME_EVENT.ENDED_AT.isNull)
            .orderBy(ICMP_UPTIME_EVENT.UPDATED_AT.desc())
            .limit(1)
    )

    @Suppress("LongMethod", "UnsafeCallOnNullableType")
    private fun monitorDetailsSelect(): SelectConditionStep<out Record?> = dslContext
        .select(
            ICMP_MONITOR.ID.`as`(IcmpMonitorDetailsDto::id.name),
            ICMP_MONITOR.NAME.`as`(IcmpMonitorDetailsDto::name.name),
            ICMP_MONITOR.HOST.`as`(IcmpMonitorDetailsDto::host.name),
            ICMP_MONITOR.UPTIME_CHECK_INTERVAL.`as`(IcmpMonitorDetailsDto::uptimeCheckInterval.name),
            ICMP_MONITOR.PACKET_COUNT.`as`(IcmpMonitorDetailsDto::packetCount.name),
            ICMP_MONITOR.TIMEOUT_SECONDS.`as`(IcmpMonitorDetailsDto::timeoutSeconds.name),
            ICMP_MONITOR.PACKET_LOSS_THRESHOLD.`as`(IcmpMonitorDetailsDto::packetLossThreshold.name),
            ICMP_MONITOR.FAILURE_COUNT_THRESHOLD.`as`(IcmpMonitorDetailsDto::failureCountThreshold.name),
            ICMP_MONITOR.METRICS_HISTORY_ENABLED.`as`(IcmpMonitorDetailsDto::metricsHistoryEnabled.name),
            ICMP_MONITOR.ENABLED.`as`(IcmpMonitorDetailsDto::enabled.name),
            ICMP_MONITOR.CREATED_AT.`as`(IcmpMonitorDetailsDto::createdAt.name),
            ICMP_MONITOR.UPDATED_AT.`as`(IcmpMonitorDetailsDto::updatedAt.name),
            latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.STATUS)!!.`as`(IcmpMonitorDetailsDto::uptimeStatus.name),
            latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.STARTED_AT)!!
                .`as`(IcmpMonitorDetailsDto::uptimeStatusStartedAt.name),
            latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.UPDATED_AT)!!
                .`as`(IcmpMonitorDetailsDto::lastUptimeCheck.name),
            latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.ERROR)!!.`as`(IcmpMonitorDetailsDto::uptimeError.name),
            DSL.array(arrayOf<String>()).`as`(IcmpMonitorDetailsDto::effectiveIntegrations.name),
            ICMP_MONITOR.INTEGRATIONS.`as`(IcmpMonitorDetailsDto::integrations.name),
            DSL.coalesce(statusPagesSubselect.field("slugs"), DSL.array(arrayOf<String>()))
                .`as`(IcmpMonitorDetailsDto::statusPages.name),
            // Placeholders for fields populated by the actions layer, not by SQL
            DSL.array(arrayOf<String>()).`as`(IcmpMonitorDetailsDto::maintenanceWindows.name),
            DSL.inline(false).`as`(IcmpMonitorDetailsDto::inMaintenance.name),
        )
        .from(ICMP_MONITOR)
        .leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        .leftJoin(statusPagesSubselect)
        .on(
            monitorNameField
                .eq(
                    DSL.`val`(MonitorType.ICMP.identifier)
                        .concat(":")
                        .concat(ICMP_MONITOR.NAME)
                )
        )
        .where(DSL.trueCondition())
}
