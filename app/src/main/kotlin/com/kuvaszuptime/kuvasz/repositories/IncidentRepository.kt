package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.SSL_EVENT
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.HttpUptimeEvent.HTTP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushUptimeEvent.PUSH_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.and
import java.time.Duration

@Singleton
class IncidentRepository(private val dslContext: DSLContext) {

    /**
     * Fetches incidents, sorted by their update date in descending order,
     * optionally filtered by [monitorId] and/or [period], and whether to include resolved incidents.
     * Returns monitor-type agnostic incident data.
     *
     * @param monitorId Optional ID of the monitor to filter incidents.
     * @param period Optional duration to filter incidents that were open during this time frame.
     * @param includeResolved Whether to include resolved incidents.
     *
     * @return List of [IncidentDto] matching the criteria.
     */
    fun getIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)

        return dslContext
            // HTTP incidents
            .httpUptimeIncidentSelect(monitorId, period, includeResolved)
            // Push incidents
            .unionAll(dslContext.pushUptimeIncidentSelect(monitorId, period, includeResolved))
            // SSL incidents
            .unionAll(dslContext.sslIncidentsSelect(monitorId, period, includeResolved))
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.httpUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean
    ) = this
        .select(
            HTTP_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            HTTP_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            HTTP_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.HTTP.name).`as`(IncidentDto::incidentType.name),
            DSL.`when`(HTTP_UPTIME_EVENT.ENDED_AT.isNull, IncidentStatus.ONGOING.name)
                .otherwise(IncidentStatus.RESOLVED.name).`as`(IncidentDto::status.name),
            HTTP_UPTIME_EVENT.ERROR.`as`(IncidentDto::details.name),
            HTTP_UPTIME_EVENT.STARTED_AT.`as`(IncidentDto::startedAt.name),
            HTTP_UPTIME_EVENT.ENDED_AT.`as`(IncidentDto::endedAt.name),
            HTTP_UPTIME_EVENT.UPDATED_AT.`as`(IncidentDto::updatedAt.name),
        )
        .from(HTTP_UPTIME_EVENT)
        .join(HTTP_MONITOR).on(HTTP_UPTIME_EVENT.MONITOR_ID.eq(HTTP_MONITOR.ID))
        .where(HTTP_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .apply {
            // Filter for monitors
            if (monitorId != null) {
                and(HTTP_MONITOR.ID.eq(monitorId))
            } else {
                and(HTTP_MONITOR.ENABLED.isTrue)
            }
            // Filter for events that were open at any point during the specified period
            period?.let {
                val periodStart = getCurrentTimestamp().minus(period)
                and(DSL.coalesce(HTTP_UPTIME_EVENT.ENDED_AT, DSL.now()).greaterThan(periodStart))
            }
            // Filter out resolved incidents if not requested
            if (!includeResolved) {
                and(HTTP_UPTIME_EVENT.ENDED_AT.isNull)
            }
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.pushUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean
    ) = this
        .select(
            PUSH_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            PUSH_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            PUSH_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.PUSH.name).`as`(IncidentDto::incidentType.name),
            DSL.`when`(PUSH_UPTIME_EVENT.ENDED_AT.isNull, IncidentStatus.ONGOING.name)
                .otherwise(IncidentStatus.RESOLVED.name).`as`(IncidentDto::status.name),
            PUSH_UPTIME_EVENT.ERROR.`as`(IncidentDto::details.name),
            PUSH_UPTIME_EVENT.STARTED_AT.`as`(IncidentDto::startedAt.name),
            PUSH_UPTIME_EVENT.ENDED_AT.`as`(IncidentDto::endedAt.name),
            PUSH_UPTIME_EVENT.UPDATED_AT.`as`(IncidentDto::updatedAt.name),
        )
        .from(PUSH_UPTIME_EVENT)
        .join(PUSH_MONITOR).on(PUSH_UPTIME_EVENT.MONITOR_ID.eq(PUSH_MONITOR.ID))
        .where(PUSH_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .apply {
            // Filter for monitors
            if (monitorId != null) {
                and(PUSH_MONITOR.ID.eq(monitorId))
            } else {
                and(PUSH_MONITOR.ENABLED.isTrue)
            }
            // Filter for events that were open at any point during the specified period
            period?.let {
                val periodStart = getCurrentTimestamp().minus(period)
                and(DSL.coalesce(PUSH_UPTIME_EVENT.ENDED_AT, DSL.now()).greaterThan(periodStart))
            }
            // Filter out resolved incidents if not requested
            if (!includeResolved) {
                and(PUSH_UPTIME_EVENT.ENDED_AT.isNull)
            }
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.sslIncidentsSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ) = this
        .select(
            HTTP_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            HTTP_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            DSL.field(HTTP_MONITOR.ENABLED.and(HTTP_MONITOR.SSL_CHECK_ENABLED))
                .`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.SSL.name).`as`(IncidentDto::incidentType.name),
            DSL.`when`(SSL_EVENT.ENDED_AT.isNull, IncidentStatus.ONGOING.name)
                .otherwise(IncidentStatus.RESOLVED.name).`as`(IncidentDto::status.name),
            SSL_EVENT.ERROR.`as`(IncidentDto::details.name),
            SSL_EVENT.STARTED_AT.`as`(IncidentDto::startedAt.name),
            SSL_EVENT.ENDED_AT.`as`(IncidentDto::endedAt.name),
            SSL_EVENT.UPDATED_AT.`as`(IncidentDto::updatedAt.name),
        )
        .from(SSL_EVENT)
        .join(HTTP_MONITOR).on(SSL_EVENT.MONITOR_ID.eq(HTTP_MONITOR.ID))
        .where(SSL_EVENT.STATUS.eq(SslStatus.INVALID))
        .apply {
            // Filter for monitors
            if (monitorId != null) {
                and(HTTP_MONITOR.ID.eq(monitorId))
            } else {
                and(HTTP_MONITOR.ENABLED.isTrue).and(HTTP_MONITOR.SSL_CHECK_ENABLED.isTrue)
            }
            // Filter for events that were open at any point during the specified period
            period?.let {
                val periodStart = getCurrentTimestamp().minus(period)
                and(DSL.coalesce(SSL_EVENT.ENDED_AT, DSL.now()).greaterThan(periodStart))
            }
            // Filter out resolved incidents if not requested
            if (!includeResolved) {
                and(SSL_EVENT.ENDED_AT.isNull)
            }
        }

    fun getHttpUptimeIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)

        return dslContext
            .httpUptimeIncidentSelect(monitorId, period, includeResolved)
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    fun getPushUptimeIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)

        return dslContext
            .pushUptimeIncidentSelect(monitorId, period, includeResolved)
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    fun getSslIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)

        return dslContext
            .sslIncidentsSelect(monitorId, period, includeResolved)
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }
}
