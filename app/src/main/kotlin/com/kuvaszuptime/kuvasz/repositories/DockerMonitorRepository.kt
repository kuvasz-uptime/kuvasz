package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.UNIQUE_DOCKER_MONITOR_NAME
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DockerMonitor.DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DockerUptimeEvent.DOCKER_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.models.monitor.MonitorIDWithName
import com.kuvaszuptime.kuvasz.models.monitor.docker.idWithName
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
class DockerMonitorRepository(
    private val dslContext: DSLContext,
) : MonitorRepository<DockerMonitorRecord, DockerMonitorDetailsDto> {

    override fun fetchAllWithDetails(
        enabled: Boolean?,
        monitorNames: List<String>?,
        categories: List<String>?,
    ): List<DockerMonitorDetailsDto> =
        getMonitorsWithDetails(enabled = enabled, monitorNames = monitorNames, categories = categories)

    override fun fetchSummaries(): List<DockerMonitorSummary> = dslContext
        .select(
            DOCKER_MONITOR.ID.`as`(DockerMonitorSummary::id.name),
            DOCKER_MONITOR.NAME.`as`(DockerMonitorSummary::name.name),
            DOCKER_MONITOR.ENABLED.`as`(DockerMonitorSummary::enabled.name),
            DOCKER_MONITOR.CATEGORY.`as`(DockerMonitorSummary::category.name),
            checkNotNull(latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.STATUS))
                .`as`(DockerMonitorSummary::uptimeStatus.name),
        )
        .from(DOCKER_MONITOR)
        .leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        .fetchInto(DockerMonitorSummary::class.java)

    override fun findById(monitorId: Long, txCtx: DSLContext?): DockerMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(DOCKER_MONITOR)
        .where(DOCKER_MONITOR.ID.eq(monitorId))
        .fetchOne()

    override fun findByName(name: String, txCtx: DSLContext?): DockerMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(DOCKER_MONITOR)
        .where(DOCKER_MONITOR.NAME.eq(name))
        .fetchOne()

    fun fetchAll(): List<DockerMonitorRecord> = dslContext
        .selectFrom(DOCKER_MONITOR)
        .fetch()

    override fun fetchByEnabled(enabled: Boolean): List<DockerMonitorRecord> = dslContext
        .selectFrom(DOCKER_MONITOR)
        .where(DOCKER_MONITOR.ENABLED.eq(enabled))
        .fetch()

    override fun fetchDistinctCategories(): List<String> = dslContext
        .selectDistinct(DOCKER_MONITOR.CATEGORY)
        .from(DOCKER_MONITOR)
        .where(DOCKER_MONITOR.CATEGORY.isNotNull)
        .fetch(DOCKER_MONITOR.CATEGORY)

    override fun deleteById(monitorId: Long, txCtx: DSLContext?): Int = (txCtx ?: dslContext)
        .deleteFrom(DOCKER_MONITOR)
        .where(DOCKER_MONITOR.ID.eq(monitorId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
        monitorNames: List<String>? = null,
        categories: List<String>? = null,
        categoryFilter: CategoryFilter? = null,
    ): List<DockerMonitorDetailsDto> =
        monitorDetailsSelect()
            .and(filterCondition(enabled, uptimeStatus, categoryFilter))
            .apply {
                selectionCondition(DOCKER_MONITOR.NAME, DOCKER_MONITOR.CATEGORY, monitorNames, categories)
                    ?.let { and(it) }
                sortedBy?.let { orderBy(it, DOCKER_MONITOR.ID.asc()) }
            }
            .fetchInto(DockerMonitorDetailsDto::class.java)

    fun getMonitorsPage(
        enabled: Boolean?,
        uptimeStatus: List<UptimeStatus>,
        categoryFilter: CategoryFilter?,
        name: String?,
        sorting: Sorting<DockerMonitorSortField>,
        pageRequest: PageRequest,
    ): PagedResult<DockerMonitorDetailsDto> {
        val condition = DSL.and(
            filterCondition(enabled, uptimeStatus, categoryFilter),
            containsIgnoreCaseCondition(DOCKER_MONITOR.NAME, name),
        )
        return monitorDetailsSelect()
            .and(condition)
            .fetchPage(
                countQuery = countSelect(uptimeStatus, condition),
                pageRequest = pageRequest,
                orderBy = listOf(sorting.field.toField().sortedIn(sorting.order), DOCKER_MONITOR.ID.asc()),
                type = DockerMonitorDetailsDto::class.java,
            )
    }

    private fun filterCondition(
        enabled: Boolean?,
        uptimeStatus: List<UptimeStatus>,
        categoryFilter: CategoryFilter?,
    ): Condition = DSL.and(
        listOfNotNull(
            enabled?.let { DOCKER_MONITOR.ENABLED.eq(it) },
            uptimeStatus.takeIf { it.isNotEmpty() }
                ?.let { checkNotNull(latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.STATUS)).`in`(it) },
            categoryFilterCondition(DOCKER_MONITOR.CATEGORY, categoryFilter),
        )
    )

    /**
     * Counts the monitors [condition] selects, joining only what it can refer to: the latest uptime event only when it
     * filters by the uptime status. The status pages can't change the number of the rows, so they are never joined.
     */
    private fun countSelect(uptimeStatus: List<UptimeStatus>, condition: Condition): Select<Record1<Int>> {
        val monitors = if (uptimeStatus.isEmpty()) {
            DOCKER_MONITOR
        } else {
            DOCKER_MONITOR.leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        }
        return dslContext
            .selectCount()
            .from(monitors)
            .where(condition)
    }

    private fun DockerMonitorSortField.toField(): Field<*> = when (this) {
        DockerMonitorSortField.ID -> DOCKER_MONITOR.ID
        DockerMonitorSortField.NAME -> DOCKER_MONITOR.NAME
        DockerMonitorSortField.ENABLED -> DOCKER_MONITOR.ENABLED
        DockerMonitorSortField.UPTIME_STATUS -> checkNotNull(latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.STATUS))
        DockerMonitorSortField.UPTIME_STATUS_STARTED_AT ->
            checkNotNull(latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.STARTED_AT))
        DockerMonitorSortField.LAST_UPTIME_CHECK ->
            checkNotNull(latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.UPDATED_AT))
        DockerMonitorSortField.UPTIME_CHECK_INTERVAL -> DOCKER_MONITOR.UPTIME_CHECK_INTERVAL
        DockerMonitorSortField.DOCKER_HOST -> DOCKER_MONITOR.DOCKER_HOST
        DockerMonitorSortField.CONTAINER -> DOCKER_MONITOR.CONTAINER
        DockerMonitorSortField.CREATED_AT -> DOCKER_MONITOR.CREATED_AT
        DockerMonitorSortField.UPDATED_AT -> DOCKER_MONITOR.UPDATED_AT
    }

    fun getMonitorWithDetails(monitorId: Long): DockerMonitorDetailsDto? =
        monitorDetailsSelect()
            .and(DOCKER_MONITOR.ID.eq(monitorId))
            .fetchOneInto(DockerMonitorDetailsDto::class.java)

    fun returningInsert(monitor: DockerMonitorRecord): DockerMonitorRecord =
        try {
            dslContext
                .insertInto(DOCKER_MONITOR)
                .set(monitor)
                .returning(DOCKER_MONITOR.asterisk())
                .fetchOneOrThrow<DockerMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    override fun returningUpdate(
        updatedMonitor: DockerMonitorRecord,
        txCtx: DSLContext?,
    ): DockerMonitorRecord =
        try {
            (txCtx ?: dslContext)
                .update(DOCKER_MONITOR)
                .set(DOCKER_MONITOR.NAME, updatedMonitor.name)
                .set(DOCKER_MONITOR.DOCKER_HOST, updatedMonitor.dockerHost)
                .set(DOCKER_MONITOR.CONTAINER, updatedMonitor.container)
                .set(DOCKER_MONITOR.UPTIME_CHECK_INTERVAL, updatedMonitor.uptimeCheckInterval)
                .set(DOCKER_MONITOR.TIMEOUT_MS, updatedMonitor.timeoutMs)
                .set(DOCKER_MONITOR.FAILURE_COUNT_THRESHOLD, updatedMonitor.failureCountThreshold)
                .set(DOCKER_MONITOR.ENABLED, updatedMonitor.enabled)
                .set(DOCKER_MONITOR.INTEGRATIONS, updatedMonitor.integrations)
                .set(DOCKER_MONITOR.METRICS_HISTORY_ENABLED, updatedMonitor.metricsHistoryEnabled)
                .set(DOCKER_MONITOR.RESTART_ALERT_ENABLED, updatedMonitor.restartAlertEnabled)
                .set(DOCKER_MONITOR.CATEGORY, updatedMonitor.normalizedCategory)
                .set(DOCKER_MONITOR.IGNORE_CONNECTIVITY_CHECK, updatedMonitor.ignoreConnectivityCheck)
                .set(DOCKER_MONITOR.UPDATED_AT, getCurrentTimestamp())
                .where(DOCKER_MONITOR.ID.eq(updatedMonitor.id))
                .returning(DOCKER_MONITOR.asterisk())
                .fetchOneOrThrow<DockerMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    override fun upsert(monitor: DockerMonitorRecord, txCtx: DSLContext?): DockerMonitorRecord = (txCtx ?: dslContext)
        .insertInto(DOCKER_MONITOR)
        .set(monitor)
        .onConflictOnConstraint(UNIQUE_DOCKER_MONITOR_NAME)
        .doUpdate()
        .setNonKeyToExcluded()
        .set(DOCKER_MONITOR.UPDATED_AT, getCurrentTimestamp())
        .returning(DOCKER_MONITOR.asterisk())
        .fetchOneOrThrow()

    override fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext?): List<MonitorIDWithName> =
        (txCtx ?: dslContext)
            .deleteFrom(DOCKER_MONITOR)
            .where(DOCKER_MONITOR.ID.notIn(ignoredIds))
            .returning(DOCKER_MONITOR.ID, DOCKER_MONITOR.NAME)
            .fetch()
            .map { it.idWithName() }

    fun updateIntegrations(monitorId: Long, newIntegrations: Array<IntegrationID>) {
        dslContext
            .update(DOCKER_MONITOR)
            .set(DOCKER_MONITOR.INTEGRATIONS, newIntegrations)
            .where(DOCKER_MONITOR.ID.eq(monitorId))
            .execute()
    }

    val latestUptimeEventSelect: Table<DockerUptimeEventRecord?> = DSL.table(
        DSL.selectFrom(DOCKER_UPTIME_EVENT)
            .where(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(DOCKER_MONITOR.ID))
            .and(DOCKER_UPTIME_EVENT.ENDED_AT.isNull)
            .orderBy(DOCKER_UPTIME_EVENT.UPDATED_AT.desc())
            .limit(1)
    )

    @Suppress("LongMethod", "UnsafeCallOnNullableType")
    private fun monitorDetailsSelect(): SelectConditionStep<out Record?> = dslContext
        .select(
            DOCKER_MONITOR.ID.`as`(DockerMonitorDetailsDto::id.name),
            DOCKER_MONITOR.NAME.`as`(DockerMonitorDetailsDto::name.name),
            DOCKER_MONITOR.CATEGORY.`as`(DockerMonitorDetailsDto::category.name),
            DOCKER_MONITOR.IGNORE_CONNECTIVITY_CHECK
                .`as`(DockerMonitorDetailsDto::ignoreConnectivityCheck.name),
            DOCKER_MONITOR.DOCKER_HOST.`as`(DockerMonitorDetailsDto::dockerHost.name),
            DOCKER_MONITOR.CONTAINER.`as`(DockerMonitorDetailsDto::container.name),
            DOCKER_MONITOR.UPTIME_CHECK_INTERVAL.`as`(DockerMonitorDetailsDto::uptimeCheckInterval.name),
            DOCKER_MONITOR.TIMEOUT_MS.`as`(DockerMonitorDetailsDto::timeoutMs.name),
            DOCKER_MONITOR.FAILURE_COUNT_THRESHOLD.`as`(DockerMonitorDetailsDto::failureCountThreshold.name),
            DOCKER_MONITOR.METRICS_HISTORY_ENABLED.`as`(DockerMonitorDetailsDto::metricsHistoryEnabled.name),
            DOCKER_MONITOR.RESTART_ALERT_ENABLED.`as`(DockerMonitorDetailsDto::restartAlertEnabled.name),
            DOCKER_MONITOR.ENABLED.`as`(DockerMonitorDetailsDto::enabled.name),
            DOCKER_MONITOR.CREATED_AT.`as`(DockerMonitorDetailsDto::createdAt.name),
            DOCKER_MONITOR.UPDATED_AT.`as`(DockerMonitorDetailsDto::updatedAt.name),
            latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.STATUS)!!
                .`as`(DockerMonitorDetailsDto::uptimeStatus.name),
            latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.STARTED_AT)!!
                .`as`(DockerMonitorDetailsDto::uptimeStatusStartedAt.name),
            latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.UPDATED_AT)!!
                .`as`(DockerMonitorDetailsDto::lastUptimeCheck.name),
            latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.ERROR)!!.`as`(DockerMonitorDetailsDto::uptimeError.name),
            latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.IMAGE)!!.`as`(DockerMonitorDetailsDto::image.name),
            latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.RESTART_COUNT)!!
                .`as`(DockerMonitorDetailsDto::restartCount.name),
            latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.CONTAINER_CREATED_AT)!!
                .`as`(DockerMonitorDetailsDto::containerCreatedAt.name),
            DSL.array(arrayOf<String>()).`as`(DockerMonitorDetailsDto::effectiveIntegrations.name),
            DOCKER_MONITOR.INTEGRATIONS.`as`(DockerMonitorDetailsDto::integrations.name),
            DSL.coalesce(statusPagesSubselect.field("slugs"), DSL.array(arrayOf<String>()))
                .`as`(DockerMonitorDetailsDto::statusPages.name),
            // Placeholders for fields populated by the actions layer, not by SQL
            DSL.array(arrayOf<String>()).`as`(DockerMonitorDetailsDto::maintenanceWindows.name),
            DSL.inline(false).`as`(DockerMonitorDetailsDto::inMaintenance.name),
        )
        .from(DOCKER_MONITOR)
        .leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        .leftJoin(statusPagesSubselect)
        .on(
            monitorNameField
                .eq(
                    DSL.`val`(monitorType.identifier)
                        .concat(":")
                        .concat(DOCKER_MONITOR.NAME)
                )
        )
        .where(DSL.trueCondition())
}
