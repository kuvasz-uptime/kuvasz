package com.kuvaszuptime.kuvasz.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kuvaszuptime.kuvasz.jooq.JsonNodeToMapConverter
import com.kuvaszuptime.kuvasz.jooq.Keys.UNIQUE_MONITOR_NAME
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.HttpUptimeEvent.HTTP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
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
class HttpMonitorRepository(private val dslContext: DSLContext) : MonitorRepository<HttpMonitorRecord> {

    override fun findById(monitorId: Long, txCtx: DSLContext?): HttpMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(HTTP_MONITOR)
        .where(HTTP_MONITOR.ID.eq(monitorId))
        .fetchOne()

    fun findByName(name: String): HttpMonitorRecord? = dslContext
        .selectFrom(HTTP_MONITOR)
        .where(HTTP_MONITOR.NAME.eq(name))
        .fetchOne()

    fun fetchAll(): List<HttpMonitorRecord> = dslContext
        .selectFrom(HTTP_MONITOR)
        .fetch()

    fun fetchByEnabled(enabled: Boolean): List<HttpMonitorRecord> = dslContext
        .selectFrom(HTTP_MONITOR)
        .where(HTTP_MONITOR.ENABLED.eq(enabled))
        .fetch()

    override fun deleteById(monitorId: Long, txCtx: DSLContext?): Int = (txCtx ?: dslContext)
        .deleteFrom(HTTP_MONITOR)
        .where(HTTP_MONITOR.ID.eq(monitorId))
        .execute()

    @Suppress("IgnoredReturnValue", "UnsafeCallOnNullableType")
    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sslStatus: List<SslStatus> = emptyList(),
        sslCheckEnabled: Boolean? = null,
        sortedBy: SortField<*>? = null,
        monitorNames: List<String>? = null,
    ): List<HttpMonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                enabled?.let { and(HTTP_MONITOR.ENABLED.eq(it)) }
                uptimeStatus.takeIf { it.isNotEmpty() }?.let {
                    and(latestUptimeEventSelect.field(HTTP_UPTIME_EVENT.STATUS)!!.`in`(it))
                }
                sslStatus.takeIf { it.isNotEmpty() }?.let { and(SSL_EVENT.STATUS.`in`(it)) }
                sslCheckEnabled?.let { and(HTTP_MONITOR.SSL_CHECK_ENABLED.eq(it)) }
                monitorNames?.let { and(HTTP_MONITOR.NAME.`in`(it)) }
                sortedBy?.let { orderBy(it) }
            }
            .fetchInto(HttpMonitorDetailsDto::class.java)

    fun getMonitorWithDetails(monitorId: Long): HttpMonitorDetailsDto? =
        monitorDetailsSelect()
            .and(HTTP_MONITOR.ID.eq(monitorId))
            .fetchOneInto(HttpMonitorDetailsDto::class.java)

    fun returningInsert(monitor: HttpMonitorRecord): Either<PersistenceException, HttpMonitorRecord> =
        try {
            Either.Right(
                dslContext
                    .insertInto(HTTP_MONITOR)
                    .set(monitor)
                    .returning(HTTP_MONITOR.asterisk())
                    .fetchOneOrThrow<HttpMonitorRecord>()
            )
        } catch (e: DataAccessException) {
            e.checkForDuplication().left()
        }

    fun returningUpdate(
        updatedMonitor: HttpMonitorRecord,
        txCtx: DSLContext = dslContext,
    ): Either<PersistenceException, HttpMonitorRecord> =
        try {
            txCtx
                .update(HTTP_MONITOR)
                .set(HTTP_MONITOR.NAME, updatedMonitor.name)
                .set(HTTP_MONITOR.URL, updatedMonitor.url)
                .set(HTTP_MONITOR.SENSITIVE_URL, updatedMonitor.sensitiveUrl)
                .set(HTTP_MONITOR.UPTIME_CHECK_INTERVAL, updatedMonitor.uptimeCheckInterval)
                .set(HTTP_MONITOR.ENABLED, updatedMonitor.enabled)
                .set(HTTP_MONITOR.SSL_CHECK_ENABLED, updatedMonitor.sslCheckEnabled)
                .set(HTTP_MONITOR.UPDATED_AT, getCurrentTimestamp())
                .set(HTTP_MONITOR.REQUEST_METHOD, updatedMonitor.requestMethod)
                .set(HTTP_MONITOR.FOLLOW_REDIRECTS, updatedMonitor.followRedirects)
                .set(HTTP_MONITOR.LATENCY_HISTORY_ENABLED, updatedMonitor.latencyHistoryEnabled)
                .set(HTTP_MONITOR.FORCE_NO_CACHE, updatedMonitor.forceNoCache)
                .set(HTTP_MONITOR.SSL_EXPIRY_THRESHOLD, updatedMonitor.sslExpiryThreshold)
                .set(HTTP_MONITOR.FAILURE_COUNT_THRESHOLD, updatedMonitor.failureCountThreshold)
                .set(HTTP_MONITOR.INTEGRATIONS, updatedMonitor.integrations)
                .set(HTTP_MONITOR.EXPECTED_STATUS_CODES, updatedMonitor.expectedStatusCodes)
                .set(HTTP_MONITOR.RESPONSE_TIME_THRESHOLD_MILLIS, updatedMonitor.responseTimeThresholdMillis)
                .set(HTTP_MONITOR.EXPECTED_KEYWORD, updatedMonitor.expectedKeyword)
                .set(HTTP_MONITOR.EXPECTED_KEYWORD_CASE_SENSITIVE, updatedMonitor.expectedKeywordCaseSensitive)
                .set(HTTP_MONITOR.EXPECTED_KEYWORD_NEGATED, updatedMonitor.expectedKeywordNegated)
                .set(HTTP_MONITOR.REQUEST_HEADERS, updatedMonitor.requestHeaders)
                .set(HTTP_MONITOR.EXPECTED_HEADERS, updatedMonitor.expectedHeaders)
                .set(HTTP_MONITOR.REQUEST_BODY, updatedMonitor.requestBody)
                .where(HTTP_MONITOR.ID.eq(updatedMonitor.id))
                .returning(HTTP_MONITOR.asterisk())
                .fetchOneOrThrow<HttpMonitorRecord>().right()
        } catch (e: DataAccessException) {
            e.checkForDuplication().left()
        }

    /**
     * Inserts a new monitor or updates an existing one if the name already exists.
     */
    fun upsert(monitor: HttpMonitorRecord, txCtx: DSLContext = this.dslContext): HttpMonitorRecord = txCtx
        .insertInto(HTTP_MONITOR)
        .set(monitor)
        .onConflictOnConstraint(UNIQUE_MONITOR_NAME)
        .doUpdate()
        .setNonKeyToExcluded()
        .set(HTTP_MONITOR.UPDATED_AT, getCurrentTimestamp())
        .returning(HTTP_MONITOR.asterisk())
        .fetchOneOrThrow()

    fun updateIntegrations(monitorId: Long, newIntegrations: Array<IntegrationID>) {
        dslContext
            .update(HTTP_MONITOR)
            .set(HTTP_MONITOR.INTEGRATIONS, newIntegrations)
            .where(HTTP_MONITOR.ID.eq(monitorId))
            .execute()
    }

    /**
     * Deletes all monitors except the ones with the given IDs.
     */
    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext = this.dslContext): Int = txCtx
        .deleteFrom(HTTP_MONITOR)
        .where(HTTP_MONITOR.ID.notIn(ignoredIds))
        .execute()

    val latestUptimeEventSelect: Table<HttpUptimeEventRecord?> = DSL.table(
        DSL.selectFrom(HTTP_UPTIME_EVENT)
            .where(HTTP_UPTIME_EVENT.MONITOR_ID.eq(HTTP_MONITOR.ID))
            .and(HTTP_UPTIME_EVENT.ENDED_AT.isNull)
            .orderBy(HTTP_UPTIME_EVENT.UPDATED_AT.desc())
            .limit(1)
    )

    @Suppress("LongMethod", "UnsafeCallOnNullableType")
    private fun monitorDetailsSelect(): SelectConditionStep<Record?> = dslContext
        .select(
            HTTP_MONITOR.ID.`as`(HttpMonitorDetailsDto::id.name),
            HTTP_MONITOR.NAME.`as`(HttpMonitorDetailsDto::name.name),
            HTTP_MONITOR.URL.`as`(HttpMonitorDetailsDto::url.name),
            HTTP_MONITOR.SENSITIVE_URL.`as`(HttpMonitorDetailsDto::sensitiveUrl.name),
            HTTP_MONITOR.UPTIME_CHECK_INTERVAL.`as`(HttpMonitorDetailsDto::uptimeCheckInterval.name),
            HTTP_MONITOR.ENABLED.`as`(HttpMonitorDetailsDto::enabled.name),
            HTTP_MONITOR.SSL_CHECK_ENABLED.`as`(HttpMonitorDetailsDto::sslCheckEnabled.name),
            HTTP_MONITOR.CREATED_AT.`as`(HttpMonitorDetailsDto::createdAt.name),
            HTTP_MONITOR.UPDATED_AT.`as`(HttpMonitorDetailsDto::updatedAt.name),
            latestUptimeEventSelect.field(HTTP_UPTIME_EVENT.STATUS)!!.`as`(HttpMonitorDetailsDto::uptimeStatus.name),
            latestUptimeEventSelect.field(HTTP_UPTIME_EVENT.STARTED_AT)!!
                .`as`(HttpMonitorDetailsDto::uptimeStatusStartedAt.name),
            latestUptimeEventSelect.field(HTTP_UPTIME_EVENT.UPDATED_AT)!!
                .`as`(HttpMonitorDetailsDto::lastUptimeCheck.name),
            SSL_EVENT.STATUS.`as`(HttpMonitorDetailsDto::sslStatus.name),
            SSL_EVENT.STARTED_AT.`as`(HttpMonitorDetailsDto::sslStatusStartedAt.name),
            SSL_EVENT.UPDATED_AT.`as`(HttpMonitorDetailsDto::lastSSLCheck.name),
            SSL_EVENT.SSL_EXPIRY_DATE.`as`(HttpMonitorDetailsDto::sslValidUntil.name),
            latestUptimeEventSelect.field(HTTP_UPTIME_EVENT.ERROR)!!.`as`(HttpMonitorDetailsDto::uptimeError.name),
            SSL_EVENT.ERROR.`as`(HttpMonitorDetailsDto::sslError.name),
            HTTP_MONITOR.LATENCY_HISTORY_ENABLED.`as`(HttpMonitorDetailsDto::latencyHistoryEnabled.name),
            HTTP_MONITOR.FORCE_NO_CACHE.`as`(HttpMonitorDetailsDto::forceNoCache.name),
            HTTP_MONITOR.FOLLOW_REDIRECTS.`as`(HttpMonitorDetailsDto::followRedirects.name),
            HTTP_MONITOR.REQUEST_METHOD.`as`(HttpMonitorDetailsDto::requestMethod.name),
            HTTP_MONITOR.SSL_EXPIRY_THRESHOLD.`as`(HttpMonitorDetailsDto::sslExpiryThreshold.name),
            HTTP_MONITOR.FAILURE_COUNT_THRESHOLD.`as`(HttpMonitorDetailsDto::failureCountThreshold.name),
            DSL.array(arrayOf<String>()).`as`(HttpMonitorDetailsDto::effectiveIntegrations.name),
            HTTP_MONITOR.INTEGRATIONS.`as`(HttpMonitorDetailsDto::integrations.name),
            HTTP_MONITOR.EXPECTED_STATUS_CODES.`as`(HttpMonitorDetailsDto::expectedStatusCodes.name),
            HTTP_MONITOR.RESPONSE_TIME_THRESHOLD_MILLIS.`as`(HttpMonitorDetailsDto::responseTimeThresholdMillis.name),
            HTTP_MONITOR.EXPECTED_KEYWORD.`as`(HttpMonitorDetailsDto::expectedKeyword.name),
            HTTP_MONITOR.EXPECTED_KEYWORD_CASE_SENSITIVE.`as`(HttpMonitorDetailsDto::expectedKeywordCaseSensitive.name),
            HTTP_MONITOR.EXPECTED_KEYWORD_NEGATED.`as`(HttpMonitorDetailsDto::expectedKeywordNegated.name),
            HTTP_MONITOR.REQUEST_HEADERS.`as`(HttpMonitorDetailsDto::requestHeaders.name)
                .convert(JsonNodeToMapConverter()),
            HTTP_MONITOR.EXPECTED_HEADERS.`as`(HttpMonitorDetailsDto::expectedHeaders.name)
                .convert(JsonNodeToMapConverter()),
            HTTP_MONITOR.REQUEST_BODY.`as`(HttpMonitorDetailsDto::requestBody.name),
            DSL.coalesce(statusPagesSubselect.field("slugs"), DSL.array(arrayOf<String>()))
                .`as`(HttpMonitorDetailsDto::statusPages.name),
        )
        .from(HTTP_MONITOR)
        .leftJoin(DSL.lateral(latestUptimeEventSelect)).on(DSL.trueCondition())
        .leftJoin(SSL_EVENT)
        .on(HTTP_MONITOR.ID.eq(SSL_EVENT.MONITOR_ID).and(SSL_EVENT.ENDED_AT.isNull))
        .leftJoin(statusPagesSubselect)
        .on(
            monitorNameField
                .eq(
                    DSL.`val`(MonitorType.HTTP_SSL.identifier)
                        .concat(":")
                        .concat(HTTP_MONITOR.NAME)
                )
        )
        .where(DSL.trueCondition())
}
