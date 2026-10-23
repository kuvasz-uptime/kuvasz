package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.UNIQUE_ICMP_MONITOR_NAME
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMonitor.ICMP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpUptimeEvent.ICMP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.models.monitor.MonitorIDWithName
import com.kuvaszuptime.kuvasz.models.monitor.icmp.idWithName
import com.kuvaszuptime.kuvasz.models.monitor.normalizedCategory
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.fetchPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.sortedIn
import jakarta.inject.Singleton
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Record1
import org.jooq.Select
import org.jooq.SelectConditionStep
import org.jooq.SortField
import org.jooq.Table
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL

@Singleton
@Suppress("TooManyFunctions")
class IcmpMonitorRepository(
    private val dslContext: DSLContext,
) : MonitorRepository<IcmpMonitorRecord, IcmpMonitorDetailsDto> {

    override fun fetchAllWithDetails(
        enabled: Boolean?,
        monitorNames: List<String>?,
        categories: List<String>?,
    ): List<IcmpMonitorDetailsDto> =
        getMonitorsWithDetails(enabled = enabled, monitorNames = monitorNames, categories = categories)

    override fun fetchSummaries(): List<IcmpMonitorSummary> = dslContext
        .select(
            ICMP_MONITOR.ID.`as`(IcmpMonitorSummary::id.name),
            ICMP_MONITOR.NAME.`as`(IcmpMonitorSummary::name.name),
            ICMP_MONITOR.ENABLED.`as`(IcmpMonitorSummary::enabled.name),
            ICMP_MONITOR.CATEGORY.`as`(IcmpMonitorSummary::category.name),
            checkNotNull(latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.STATUS))
                .`as`(IcmpMonitorSummary::uptimeStatus.name),
        )
        .from(ICMP_MONITOR)
        .leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        .fetchInto(IcmpMonitorSummary::class.java)

    override fun findById(monitorId: Long, txCtx: DSLContext?): IcmpMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(ICMP_MONITOR)
        .where(ICMP_MONITOR.ID.eq(monitorId))
        .fetchOne()

    override fun findByName(name: String, txCtx: DSLContext?): IcmpMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(ICMP_MONITOR)
        .where(ICMP_MONITOR.NAME.eq(name))
        .fetchOne()

    fun fetchAll(): List<IcmpMonitorRecord> = dslContext
        .selectFrom(ICMP_MONITOR)
        .fetch()

    override fun fetchByEnabled(enabled: Boolean): List<IcmpMonitorRecord> = dslContext
        .selectFrom(ICMP_MONITOR)
        .where(ICMP_MONITOR.ENABLED.eq(enabled))
        .fetch()

    override fun fetchDistinctCategories(): List<String> = dslContext
        .selectDistinct(ICMP_MONITOR.CATEGORY)
        .from(ICMP_MONITOR)
        .where(ICMP_MONITOR.CATEGORY.isNotNull)
        .fetch(ICMP_MONITOR.CATEGORY)

    override fun deleteById(monitorId: Long, txCtx: DSLContext?): Int = (txCtx ?: dslContext)
        .deleteFrom(ICMP_MONITOR)
        .where(ICMP_MONITOR.ID.eq(monitorId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
        monitorNames: List<String>? = null,
        categories: List<String>? = null,
        categoryFilter: CategoryFilter? = null,
    ): List<IcmpMonitorDetailsDto> =
        monitorDetailsSelect()
            .and(filterCondition(enabled, uptimeStatus, categoryFilter))
            .apply {
                selectionCondition(ICMP_MONITOR.NAME, ICMP_MONITOR.CATEGORY, monitorNames, categories)?.let { and(it) }
                sortedBy?.let { orderBy(it, ICMP_MONITOR.ID.asc()) }
            }
            .fetchInto(IcmpMonitorDetailsDto::class.java)

    fun getMonitorsPage(
        enabled: Boolean?,
        uptimeStatus: List<UptimeStatus>,
        categoryFilter: CategoryFilter?,
        name: String?,
        sorting: Sorting<IcmpMonitorSortField>,
        pageRequest: PageRequest,
    ): PagedResult<IcmpMonitorDetailsDto> {
        val condition = DSL.and(
            filterCondition(enabled, uptimeStatus, categoryFilter),
            containsIgnoreCaseCondition(ICMP_MONITOR.NAME, name),
        )
        return monitorDetailsSelect()
            .and(condition)
            .fetchPage(
                countQuery = countSelect(uptimeStatus, condition),
                pageRequest = pageRequest,
                orderBy = listOf(sorting.field.toField().sortedIn(sorting.order), ICMP_MONITOR.ID.asc()),
                type = IcmpMonitorDetailsDto::class.java,
            )
    }

    private fun filterCondition(
        enabled: Boolean?,
        uptimeStatus: List<UptimeStatus>,
        categoryFilter: CategoryFilter?,
    ): Condition = DSL.and(
        listOfNotNull(
            enabled?.let { ICMP_MONITOR.ENABLED.eq(it) },
            uptimeStatus.takeIf { it.isNotEmpty() }
                ?.let { checkNotNull(latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.STATUS)).`in`(it) },
            categoryFilterCondition(ICMP_MONITOR.CATEGORY, categoryFilter),
        )
    )

    /**
     * Counts the monitors [condition] selects, joining only what it can refer to: the latest uptime event only when it
     * filters by the uptime status. The status pages can't change the number of the rows, so they are never joined.
     */
    private fun countSelect(uptimeStatus: List<UptimeStatus>, condition: Condition): Select<Record1<Int>> {
        val monitors = if (uptimeStatus.isEmpty()) {
            ICMP_MONITOR
        } else {
            ICMP_MONITOR.leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        }
        return dslContext
            .selectCount()
            .from(monitors)
            .where(condition)
    }

    private fun IcmpMonitorSortField.toField(): Field<*> = when (this) {
        IcmpMonitorSortField.ID -> ICMP_MONITOR.ID
        IcmpMonitorSortField.NAME -> ICMP_MONITOR.NAME
        IcmpMonitorSortField.ENABLED -> ICMP_MONITOR.ENABLED
        IcmpMonitorSortField.UPTIME_STATUS -> checkNotNull(latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.STATUS))
        IcmpMonitorSortField.UPTIME_STATUS_STARTED_AT ->
            checkNotNull(latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.STARTED_AT))
        IcmpMonitorSortField.LAST_UPTIME_CHECK ->
            checkNotNull(latestUptimeEventSelect.field(ICMP_UPTIME_EVENT.UPDATED_AT))
        IcmpMonitorSortField.UPTIME_CHECK_INTERVAL -> ICMP_MONITOR.UPTIME_CHECK_INTERVAL
        IcmpMonitorSortField.HOST -> ICMP_MONITOR.HOST
        IcmpMonitorSortField.CREATED_AT -> ICMP_MONITOR.CREATED_AT
        IcmpMonitorSortField.UPDATED_AT -> ICMP_MONITOR.UPDATED_AT
    }

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

    override fun returningUpdate(
        updatedMonitor: IcmpMonitorRecord,
        txCtx: DSLContext?,
    ): IcmpMonitorRecord =
        try {
            (txCtx ?: dslContext)
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
                .set(ICMP_MONITOR.CATEGORY, updatedMonitor.normalizedCategory)
                .set(ICMP_MONITOR.IGNORE_CONNECTIVITY_CHECK, updatedMonitor.ignoreConnectivityCheck)
                .set(ICMP_MONITOR.UPDATED_AT, getCurrentTimestamp())
                .where(ICMP_MONITOR.ID.eq(updatedMonitor.id))
                .returning(ICMP_MONITOR.asterisk())
                .fetchOneOrThrow<IcmpMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    override fun upsert(monitor: IcmpMonitorRecord, txCtx: DSLContext?): IcmpMonitorRecord = (txCtx ?: dslContext)
        .insertInto(ICMP_MONITOR)
        .set(monitor)
        .onConflictOnConstraint(UNIQUE_ICMP_MONITOR_NAME)
        .doUpdate()
        .setNonKeyToExcluded()
        .set(ICMP_MONITOR.UPDATED_AT, getCurrentTimestamp())
        .returning(ICMP_MONITOR.asterisk())
        .fetchOneOrThrow()

    override fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext?): List<MonitorIDWithName> =
        (txCtx ?: dslContext)
            .deleteFrom(ICMP_MONITOR)
            .where(ICMP_MONITOR.ID.notIn(ignoredIds))
            .returning(ICMP_MONITOR.ID, ICMP_MONITOR.NAME)
            .fetch()
            .map { it.idWithName() }

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
            ICMP_MONITOR.CATEGORY.`as`(IcmpMonitorDetailsDto::category.name),
            ICMP_MONITOR.IGNORE_CONNECTIVITY_CHECK
                .`as`(IcmpMonitorDetailsDto::ignoreConnectivityCheck.name),
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
                    DSL.`val`(monitorType.identifier)
                        .concat(":")
                        .concat(ICMP_MONITOR.NAME)
                )
        )
        .where(DSL.trueCondition())
}
