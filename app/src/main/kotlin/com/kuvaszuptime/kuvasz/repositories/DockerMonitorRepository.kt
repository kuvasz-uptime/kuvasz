package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.UNIQUE_DOCKER_MONITOR_NAME
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DockerMonitor.DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DockerUptimeEvent.DOCKER_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.models.monitor.normalizedCategory
import com.kuvaszuptime.kuvasz.models.monitor.MonitorIDWithName
import com.kuvaszuptime.kuvasz.models.monitor.docker.idWithName
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
class DockerMonitorRepository(
    private val dslContext: DSLContext,
) : MonitorRepository<DockerMonitorRecord, DockerMonitorDetailsDto> {

    override fun fetchAllWithDetails(
        enabled: Boolean?,
        monitorNames: List<String>?,
        categories: List<String>?,
    ): List<DockerMonitorDetailsDto> =
        getMonitorsWithDetails(enabled = enabled, monitorNames = monitorNames, categories = categories)

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

    @Suppress("IgnoredReturnValue", "UnsafeCallOnNullableType")
    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
        monitorNames: List<String>? = null,
        categories: List<String>? = null,
        categoryFilter: CategoryFilter? = null,
    ): List<DockerMonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                enabled?.let { and(DOCKER_MONITOR.ENABLED.eq(it)) }
                uptimeStatus.takeIf { it.isNotEmpty() }?.let {
                    and(latestUptimeEventSelect.field(DOCKER_UPTIME_EVENT.STATUS)!!.`in`(it))
                }
                selectionCondition(DOCKER_MONITOR.NAME, DOCKER_MONITOR.CATEGORY, monitorNames, categories)
                    ?.let { and(it) }
                categoryFilterCondition(DOCKER_MONITOR.CATEGORY, categoryFilter)?.let { and(it) }
                sortedBy?.let { orderBy(it, DOCKER_MONITOR.ID.asc()) }
            }
            .fetchInto(DockerMonitorDetailsDto::class.java)

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
