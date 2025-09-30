package com.kuvaszuptime.kuvasz.repositories

import arrow.core.Either
import com.kuvaszuptime.kuvasz.jooq.Keys.UNIQUE_PUSH_MONITOR_NAME
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushUptimeEvent.PUSH_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.MonitorDuplicatedException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toPersistenceError
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

@Singleton
@Suppress("TooManyFunctions")
class PushMonitorRepository(private val dslContext: DSLContext) {

    fun findById(monitorId: Long, ctx: DSLContext = dslContext): PushMonitorRecord? = ctx
        .selectFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.ID.eq(monitorId))
        .fetchOne()

    fun findByName(name: String): PushMonitorRecord? = dslContext
        .selectFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.NAME.eq(name))
        .fetchOne()

    @Suppress("IgnoredReturnValue")
    fun fetchAll(
        sortedBy: SortField<*>? = null,
    ): List<PushMonitorRecord> = dslContext
        .selectFrom(PUSH_MONITOR)
        .apply { sortedBy?.let { orderBy(it) } }
        .fetch()

    fun fetchByEnabled(enabled: Boolean): List<PushMonitorRecord> = dslContext
        .selectFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.ENABLED.eq(enabled))
        .fetch()

    fun deleteById(monitorId: Long): Int = dslContext
        .deleteFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.ID.eq(monitorId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
        monitorNames: List<String>? = null,
    ): List<PushMonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                enabled?.let { and(PUSH_MONITOR.ENABLED.eq(it)) }
                uptimeStatus.takeIf { it.isNotEmpty() }?.let { and(PUSH_UPTIME_EVENT.STATUS.`in`(it)) }
                monitorNames?.let { and(PUSH_MONITOR.NAME.`in`(it)) }
                sortedBy?.let { orderBy(it) }
            }
            .fetchInto(PushMonitorDetailsDto::class.java)

    fun getMonitorWithDetails(monitorId: Long): PushMonitorDetailsDto? =
        monitorDetailsSelect()
            .and(PUSH_MONITOR.ID.eq(monitorId))
            .fetchOneInto(PushMonitorDetailsDto::class.java)

    fun returningInsert(monitor: PushMonitorRecord): Either<PersistenceException, PushMonitorRecord> =
        try {
            Either.Right(
                dslContext
                    .insertInto(PUSH_MONITOR)
                    .set(monitor)
                    .returning(PUSH_MONITOR.asterisk())
                    .fetchOneOrThrow<PushMonitorRecord>()
            )
        } catch (e: DataAccessException) {
            e.handle()
        }

    fun returningUpdate(
        updatedMonitor: PushMonitorRecord,
        txCtx: DSLContext = dslContext,
    ): Either<PersistenceException, PushMonitorRecord> =
        try {
            Either.Right(
                txCtx
                    .update(PUSH_MONITOR)
                    .set(PUSH_MONITOR.NAME, updatedMonitor.name)
                    .set(PUSH_MONITOR.HEARTBEAT_INTERVAL, updatedMonitor.heartbeatInterval)
                    .set(PUSH_MONITOR.GRACE_PERIOD, updatedMonitor.gracePeriod)
                    .set(PUSH_MONITOR.ENABLED, updatedMonitor.enabled)
                    .set(PUSH_MONITOR.CLIENT_SECRET, updatedMonitor.clientSecret)
                    .set(PUSH_MONITOR.INTEGRATIONS, updatedMonitor.integrations)
                    .set(PUSH_MONITOR.UPDATED_AT, getCurrentTimestamp())
                    .where(PUSH_MONITOR.ID.eq(updatedMonitor.id))
                    .returning(PUSH_MONITOR.asterisk())
                    .fetchOneOrThrow<PushMonitorRecord>()
            )
        } catch (e: DataAccessException) {
            e.handle()
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
     * Deletes all monitors except the ones with the given names.
     */
    fun deleteAllExcept(ignoredNames: List<String>, txCtx: DSLContext = this.dslContext): Int = txCtx
        .deleteFrom(PUSH_MONITOR)
        .where(PUSH_MONITOR.NAME.notIn(ignoredNames))
        .execute()

    @Suppress("LongMethod")
    private fun monitorDetailsSelect(): SelectConditionStep<out Record?> {
        val monitorNameField = DSL.field("t.monitor_name", SQLDataType.VARCHAR).`as`("monitor_name")
        // TODO extract
        val statusPagesSubselect = DSL
            .select(
                monitorNameField,
                DSL.arrayAgg(STATUS_PAGE.SLUG).`as`("slugs"),
            )
            .from(STATUS_PAGE)
            .crossJoin(
                DSL.unnest(STATUS_PAGE.MONITORS).`as`("t", "monitor_name")
            )
            .groupBy(monitorNameField)

//        @param:Schema(description = PushMonitorDocs.NEXT_EXPECTED_HEARTBEAT, required = true, nullable = true)
//        val nextExpectedHeartbeatAt: OffsetDateTime?,

        return dslContext.select(
            PUSH_MONITOR.ID.`as`(PushMonitorDetailsDto::id.name),
            PUSH_MONITOR.NAME.`as`(PushMonitorDetailsDto::name.name),
            PUSH_MONITOR.HEARTBEAT_INTERVAL.`as`(PushMonitorDetailsDto::heartbeatInterval.name),
            PUSH_MONITOR.GRACE_PERIOD.`as`(PushMonitorDetailsDto::gracePeriod.name),
            PUSH_MONITOR.ENABLED.`as`(PushMonitorDetailsDto::enabled.name),
            PUSH_MONITOR.LAST_HEARTBEAT.`as`(PushMonitorDetailsDto::lastHeartbeatAt.name),
            PUSH_MONITOR.CREATED_AT.`as`(PushMonitorDetailsDto::createdAt.name),
            PUSH_MONITOR.UPDATED_AT.`as`(PushMonitorDetailsDto::updatedAt.name),
            PUSH_UPTIME_EVENT.STATUS.`as`(PushMonitorDetailsDto::uptimeStatus.name),
            PUSH_UPTIME_EVENT.STARTED_AT.`as`(PushMonitorDetailsDto::uptimeStatusStartedAt.name),
            PUSH_UPTIME_EVENT.ERROR.`as`(PushMonitorDetailsDto::uptimeError.name),
            DSL.array(arrayOf<String>()).`as`(PushMonitorDetailsDto::effectiveIntegrations.name),
            PUSH_MONITOR.INTEGRATIONS.`as`(PushMonitorDetailsDto::integrations.name),
            DSL.coalesce(statusPagesSubselect.field("slugs"), DSL.array(arrayOf<String>()))
                .`as`(PushMonitorDetailsDto::statusPages.name)
        )
            .from(PUSH_MONITOR)
            .leftJoin(PUSH_UPTIME_EVENT)
            .on(PUSH_MONITOR.ID.eq(PUSH_UPTIME_EVENT.MONITOR_ID).and(PUSH_UPTIME_EVENT.ENDED_AT.isNull))
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
    }

    /**
     * Converts a DataAccessException to a PersistenceException by matching duplication errors.
     */
    // TODO extract and reuse
    private fun DataAccessException.handle(): Either<PersistenceException, Nothing> {
        val persistenceError = toPersistenceError()
        return Either.Left(
            if (persistenceError is DuplicationException) {
                MonitorDuplicatedException()
            } else {
                persistenceError
            }
        )
    }
}
