package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.UNIQUE_PUSH_MONITOR_NAME
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushUptimeEvent.PUSH_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.SortField
import org.jooq.Table
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.time.OffsetDateTime

@Singleton
@Suppress("TooManyFunctions")
class PushMonitorRepository(private val dslContext: DSLContext) : MonitorRepository<PushMonitorRecord> {

    override fun findById(monitorId: Long, txCtx: DSLContext?): PushMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.ID.eq(monitorId))
        .fetchOne()

    fun findByName(name: String): PushMonitorRecord? = dslContext
        .selectFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.NAME.eq(name))
        .fetchOne()

    fun findEnabledByClientSecret(clientSecret: String, txtCtx: DSLContext = dslContext): PushMonitorRecord? = txtCtx
        .selectFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.CLIENT_SECRET.eq(clientSecret))
        .and(PUSH_MONITOR.ENABLED.isTrue)
        .fetchOne()

    fun findByClientSecret(clientSecret: String, txtCtx: DSLContext = dslContext): PushMonitorRecord? = txtCtx
        .selectFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.CLIENT_SECRET.eq(clientSecret))
        .fetchOne()

    fun fetchAll(): List<PushMonitorRecord> = dslContext
        .selectFrom(PUSH_MONITOR)
        .fetch()

    fun fetchByEnabled(enabled: Boolean): List<PushMonitorRecord> = dslContext
        .selectFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.ENABLED.eq(enabled))
        .fetch()

    override fun deleteById(monitorId: Long, txCtx: DSLContext?): Int = (txCtx ?: dslContext)
        .deleteFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.ID.eq(monitorId))
        .execute()

    @Suppress("IgnoredReturnValue", "UnsafeCallOnNullableType")
    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
        monitorNames: List<String>? = null,
    ): List<PushMonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                enabled?.let { and(PUSH_MONITOR.ENABLED.eq(it)) }
                uptimeStatus.takeIf { it.isNotEmpty() }?.let {
                    and(latestUptimeEventSelect.field(PUSH_UPTIME_EVENT.STATUS)!!.`in`(it))
                }
                monitorNames?.let { and(PUSH_MONITOR.NAME.`in`(it)) }
                sortedBy?.let { orderBy(it) }
            }
            .fetchInto(PushMonitorDetailsDto::class.java)

    fun getMonitorWithDetails(monitorId: Long): PushMonitorDetailsDto? =
        monitorDetailsSelect()
            .and(PUSH_MONITOR.ID.eq(monitorId))
            .fetchOneInto(PushMonitorDetailsDto::class.java)

    fun returningInsert(monitor: PushMonitorRecord): PushMonitorRecord =
        try {
            dslContext
                .insertInto(PUSH_MONITOR)
                .set(monitor)
                .returning(PUSH_MONITOR.asterisk())
                .fetchOneOrThrow<PushMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun returningUpdate(
        updatedMonitor: PushMonitorRecord,
        txCtx: DSLContext = dslContext,
    ): PushMonitorRecord =
        try {
            txCtx
                .update(PUSH_MONITOR)
                .set(PUSH_MONITOR.NAME, updatedMonitor.name)
                .set(PUSH_MONITOR.HEARTBEAT_INTERVAL, updatedMonitor.heartbeatInterval)
                .set(PUSH_MONITOR.GRACE_PERIOD, updatedMonitor.gracePeriod)
                .set(PUSH_MONITOR.ENABLED, updatedMonitor.enabled)
                .set(PUSH_MONITOR.CLIENT_SECRET, updatedMonitor.clientSecret)
                .set(PUSH_MONITOR.INTEGRATIONS, updatedMonitor.integrations)
                .set(PUSH_MONITOR.FAILURE_COUNT_THRESHOLD, updatedMonitor.failureCountThreshold)
                .set(PUSH_MONITOR.UPDATED_AT, getCurrentTimestamp())
                .where(PUSH_MONITOR.ID.eq(updatedMonitor.id))
                .returning(PUSH_MONITOR.asterisk())
                .fetchOneOrThrow<PushMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    /**
     * Inserts a new monitor or updates an existing one if the name already exists.
     */
    fun upsert(monitor: PushMonitorRecord, txCtx: DSLContext = this.dslContext): PushMonitorRecord = txCtx
        .insertInto(PUSH_MONITOR)
        .set(monitor)
        .onConflictOnConstraint(UNIQUE_PUSH_MONITOR_NAME)
        .doUpdate()
        .setNonConflictingKeyToExcluded()
        .set(PUSH_MONITOR.UPDATED_AT, getCurrentTimestamp())
        .returning(PUSH_MONITOR.asterisk())
        .fetchOneOrThrow()

    fun updateIntegrations(monitorId: Long, newIntegrations: Array<IntegrationID>) {
        dslContext
            .update(PUSH_MONITOR)
            .set(PUSH_MONITOR.INTEGRATIONS, newIntegrations)
            .where(PUSH_MONITOR.ID.eq(monitorId))
            .execute()
    }

    /**
     * Deletes all monitors except the ones with the given IDs.
     */
    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext = this.dslContext): Int = txCtx
        .deleteFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.ID.notIn(ignoredIds))
        .execute()

    val latestUptimeEventSelect: Table<PushUptimeEventRecord?> = DSL.table(
        DSL.selectFrom(PUSH_UPTIME_EVENT)
            .where(PUSH_UPTIME_EVENT.MONITOR_ID.eq(PUSH_MONITOR.ID))
            .and(PUSH_UPTIME_EVENT.ENDED_AT.isNull)
            .orderBy(PUSH_UPTIME_EVENT.UPDATED_AT.desc())
            .limit(1)
    )

    @Suppress("LongMethod", "UnsafeCallOnNullableType")
    private fun monitorDetailsSelect(): SelectConditionStep<out Record?> = dslContext
        .select(
            PUSH_MONITOR.ID.`as`(PushMonitorDetailsDto::id.name),
            PUSH_MONITOR.NAME.`as`(PushMonitorDetailsDto::name.name),
            PUSH_MONITOR.HEARTBEAT_INTERVAL.`as`(PushMonitorDetailsDto::heartbeatInterval.name),
            PUSH_MONITOR.GRACE_PERIOD.`as`(PushMonitorDetailsDto::gracePeriod.name),
            PUSH_MONITOR.CLIENT_SECRET.`as`(PushMonitorDetailsDto::clientSecret.name),
            PUSH_MONITOR.ENABLED.`as`(PushMonitorDetailsDto::enabled.name),
            PUSH_MONITOR.LAST_HEARTBEAT.`as`(PushMonitorDetailsDto::lastHeartbeat.name),
            PUSH_MONITOR.CREATED_AT.`as`(PushMonitorDetailsDto::createdAt.name),
            PUSH_MONITOR.UPDATED_AT.`as`(PushMonitorDetailsDto::updatedAt.name),
            PUSH_MONITOR.FAILURE_COUNT_THRESHOLD.`as`(PushMonitorDetailsDto::failureCountThreshold.name),
            latestUptimeEventSelect.field(PUSH_UPTIME_EVENT.STATUS)!!.`as`(PushMonitorDetailsDto::uptimeStatus.name),
            latestUptimeEventSelect.field(PUSH_UPTIME_EVENT.STARTED_AT)!!
                .`as`(PushMonitorDetailsDto::uptimeStatusStartedAt.name),
            latestUptimeEventSelect.field(PUSH_UPTIME_EVENT.UPDATED_AT)!!
                .`as`(PushMonitorDetailsDto::lastUptimeCheck.name),
            latestUptimeEventSelect.field(PUSH_UPTIME_EVENT.ERROR)!!.`as`(PushMonitorDetailsDto::uptimeError.name),
            DSL.array(arrayOf<String>()).`as`(PushMonitorDetailsDto::effectiveIntegrations.name),
            PUSH_MONITOR.INTEGRATIONS.`as`(PushMonitorDetailsDto::integrations.name),
            DSL.coalesce(statusPagesSubselect.field("slugs"), DSL.array(arrayOf<String>()))
                .`as`(PushMonitorDetailsDto::statusPages.name),
            nextExpectedHeartbeatField.`as`(PushMonitorDetailsDto::nextExpectedHeartbeat.name),
            // Placeholders for fields populated by the actions layer, not by SQL
            DSL.array(arrayOf<String>()).`as`(PushMonitorDetailsDto::maintenanceWindows.name),
            DSL.inline(false).`as`(PushMonitorDetailsDto::inMaintenance.name),
        )
        .from(PUSH_MONITOR)
        .leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        .leftJoin(statusPagesSubselect)
        .on(
            monitorNameField
                .eq(
                    DSL.`val`(MonitorType.PUSH.identifier)
                        .concat(":")
                        .concat(PUSH_MONITOR.NAME)
                )
        )
        .where(DSL.trueCondition())

    /**
     * Calculates the next expected heartbeat's timestamp as an OffsetDateTime by adding the heartbeat interval and
     * the grace period to the last heartbeat's timestamp.
     * Uses a quite ugly concatenation of query parts, because jOOQ doesn't support adding intervals to TIMESTAMPTZ
     * fields yet.
     *
     * The result can be NULL in case the last heartbeat is also NULL
     */
    val nextExpectedHeartbeatField: Field<OffsetDateTime?> = DSL.field(
        PUSH_MONITOR.LAST_HEARTBEAT.name + "+(" +
            PUSH_MONITOR.HEARTBEAT_INTERVAL.name + "+" +
            PUSH_MONITOR.GRACE_PERIOD.name +
            ") * interval '1 second'",
        SQLDataType.TIMESTAMPWITHTIMEZONE
    )

    /**
     * Fetches the push monitors that:
     * - are enabled
     * - have an already recorded heartbeat, and the next expected heartbeat is behind us
     */
    fun fetchWithMissedHeartbeats(txCtx: DSLContext?): List<PushMonitorRecord> = (txCtx ?: dslContext)
        .selectFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.ENABLED.isTrue)
        .and(PUSH_MONITOR.LAST_HEARTBEAT.isNotNull)
        .and(nextExpectedHeartbeatField.le(DSL.currentOffsetDateTime()))
        .fetch()

    /**
     * Updates a push monitor's last heartbeat to the provided timestamp by matching its client secret.
     * Doesn't check if the monitor is enabled, which is intentional to make the clients able to maintein a monitor's
     * heartbeat even if the monitor is temporarily paused, for example. So after the monitor gets resumed, we won't get
     * false positive alerts.
     */
    fun updateLastHeartbeat(
        clientSecret: String,
        timestamp: OffsetDateTime,
        txCtx: DSLContext = dslContext,
    ): PushMonitorRecord? = txCtx
        .update(PUSH_MONITOR)
        .set(PUSH_MONITOR.LAST_HEARTBEAT, timestamp)
        .set(PUSH_MONITOR.UPDATED_AT, getCurrentTimestamp())
        .where(PUSH_MONITOR.CLIENT_SECRET.eq(clientSecret))
        .returning(PUSH_MONITOR.asterisk())
        .fetchOne()
}
