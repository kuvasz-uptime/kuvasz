package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.Tables.SSL_EVENT
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DnsMonitor.DNS_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DnsUptimeEvent.DNS_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.HttpUptimeEvent.HTTP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMonitor.ICMP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpUptimeEvent.ICMP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushUptimeEvent.PUSH_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.DockerMonitor.DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DockerUptimeEvent.DOCKER_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.TcpMonitor.TCP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.TcpUptimeEvent.TCP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Select
import org.jooq.impl.DSL
import org.jooq.kotlin.and
import java.time.Duration
import java.time.OffsetDateTime

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
     * @param includeSslIncidents Whether to include the SSL incidents too, not only the uptime ones.
     *
     * @return List of [IncidentDto] matching the criteria.
     */
    fun getIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
        includeSslIncidents: Boolean = true,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)
        val incidents = incidentSelects(monitorId, period, includeResolved.toIncidentStates(), includeSslIncidents)
            .unionAll()
            .asTable("incident")

        return dslContext
            .selectFrom(incidents)
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    /**
     * Fetches the incidents of every monitor that were resolved during the given [period], the latest resolved one
     * first, but only [limit] of them.
     */
    fun getLatestResolvedIncidents(
        period: Duration,
        limit: Int,
        includeSslIncidents: Boolean = true,
    ): List<IncidentDto> {
        val endedAt = DSL.field(DSL.name(IncidentDto::endedAt.name))
        // Every type is ordered and limited on its own too, so each of them can stop after its latest incidents, read
        // through the index of their end, instead of every incident of the period being collected and sorted first
        val incidents = incidentSelects(monitorId = null, period, IncidentStates.RESOLVED, includeSslIncidents)
            .map { it.orderBy(endedAt.desc()).limit(limit) }
            .unionAll()
            .asTable("incident")

        return dslContext
            .selectFrom(incidents)
            .orderBy(endedAt.desc())
            .limit(limit)
            .fetchInto(IncidentDto::class.java)
    }

    /** The incidents of every type, each of them selected on its own. */
    private fun incidentSelects(
        monitorId: Long?,
        period: Duration?,
        states: IncidentStates,
        includeSslIncidents: Boolean,
    ) = listOfNotNull(
        dslContext.httpUptimeIncidentSelect(monitorId, period, states),
        dslContext.pushUptimeIncidentSelect(monitorId, period, states),
        dslContext.icmpUptimeIncidentSelect(monitorId, period, states),
        dslContext.tcpUptimeIncidentSelect(monitorId, period, states),
        dslContext.dnsUptimeIncidentSelect(monitorId, period, states),
        dslContext.dockerUptimeIncidentSelect(monitorId, period, states),
        if (includeSslIncidents) dslContext.sslIncidentsSelect(monitorId, period, states) else null,
    )

    private fun <R : Record> List<Select<R>>.unionAll(): Select<R> = reduce { union, select -> union.unionAll(select) }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.httpUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates
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
            and(incidentStatesCondition(HTTP_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.pushUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates
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
            and(incidentStatesCondition(PUSH_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.icmpUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates
    ) = this
        .select(
            ICMP_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            ICMP_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            ICMP_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.ICMP.name).`as`(IncidentDto::incidentType.name),
            DSL.`when`(ICMP_UPTIME_EVENT.ENDED_AT.isNull, IncidentStatus.ONGOING.name)
                .otherwise(IncidentStatus.RESOLVED.name).`as`(IncidentDto::status.name),
            ICMP_UPTIME_EVENT.ERROR.`as`(IncidentDto::details.name),
            ICMP_UPTIME_EVENT.STARTED_AT.`as`(IncidentDto::startedAt.name),
            ICMP_UPTIME_EVENT.ENDED_AT.`as`(IncidentDto::endedAt.name),
            ICMP_UPTIME_EVENT.UPDATED_AT.`as`(IncidentDto::updatedAt.name),
        )
        .from(ICMP_UPTIME_EVENT)
        .join(ICMP_MONITOR).on(ICMP_UPTIME_EVENT.MONITOR_ID.eq(ICMP_MONITOR.ID))
        .where(ICMP_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .apply {
            if (monitorId != null) {
                and(ICMP_MONITOR.ID.eq(monitorId))
            } else {
                and(ICMP_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(ICMP_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.tcpUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates
    ) = this
        .select(
            TCP_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            TCP_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            TCP_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.TCP.name).`as`(IncidentDto::incidentType.name),
            DSL.`when`(TCP_UPTIME_EVENT.ENDED_AT.isNull, IncidentStatus.ONGOING.name)
                .otherwise(IncidentStatus.RESOLVED.name).`as`(IncidentDto::status.name),
            TCP_UPTIME_EVENT.ERROR.`as`(IncidentDto::details.name),
            TCP_UPTIME_EVENT.STARTED_AT.`as`(IncidentDto::startedAt.name),
            TCP_UPTIME_EVENT.ENDED_AT.`as`(IncidentDto::endedAt.name),
            TCP_UPTIME_EVENT.UPDATED_AT.`as`(IncidentDto::updatedAt.name),
        )
        .from(TCP_UPTIME_EVENT)
        .join(TCP_MONITOR).on(TCP_UPTIME_EVENT.MONITOR_ID.eq(TCP_MONITOR.ID))
        .where(TCP_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .apply {
            if (monitorId != null) {
                and(TCP_MONITOR.ID.eq(monitorId))
            } else {
                and(TCP_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(TCP_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.dockerUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates
    ) = this
        .select(
            DOCKER_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            DOCKER_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            DOCKER_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.DOCKER.name).`as`(IncidentDto::incidentType.name),
            DSL.`when`(DOCKER_UPTIME_EVENT.ENDED_AT.isNull, IncidentStatus.ONGOING.name)
                .otherwise(IncidentStatus.RESOLVED.name).`as`(IncidentDto::status.name),
            DSL.`when`(DOCKER_UPTIME_EVENT.IMAGE.isNull, DOCKER_UPTIME_EVENT.ERROR)
                .otherwise(
                    DSL.concat(
                        DOCKER_UPTIME_EVENT.ERROR,
                        DSL.inline(" · ${Messages.dockerImageLabel()}: "),
                        DOCKER_UPTIME_EVENT.IMAGE,
                    )
                )
                .`as`(IncidentDto::details.name),
            DOCKER_UPTIME_EVENT.STARTED_AT.`as`(IncidentDto::startedAt.name),
            DOCKER_UPTIME_EVENT.ENDED_AT.`as`(IncidentDto::endedAt.name),
            DOCKER_UPTIME_EVENT.UPDATED_AT.`as`(IncidentDto::updatedAt.name),
        )
        .from(DOCKER_UPTIME_EVENT)
        .join(DOCKER_MONITOR).on(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(DOCKER_MONITOR.ID))
        .where(DOCKER_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .apply {
            if (monitorId != null) {
                and(DOCKER_MONITOR.ID.eq(monitorId))
            } else {
                and(DOCKER_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(DOCKER_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.dnsUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates
    ) = this
        .select(
            DNS_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            DNS_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            DNS_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.DNS.name).`as`(IncidentDto::incidentType.name),
            DSL.`when`(DNS_UPTIME_EVENT.ENDED_AT.isNull, IncidentStatus.ONGOING.name)
                .otherwise(IncidentStatus.RESOLVED.name).`as`(IncidentDto::status.name),
            DNS_UPTIME_EVENT.ERROR.`as`(IncidentDto::details.name),
            DNS_UPTIME_EVENT.STARTED_AT.`as`(IncidentDto::startedAt.name),
            DNS_UPTIME_EVENT.ENDED_AT.`as`(IncidentDto::endedAt.name),
            DNS_UPTIME_EVENT.UPDATED_AT.`as`(IncidentDto::updatedAt.name),
        )
        .from(DNS_UPTIME_EVENT)
        .join(DNS_MONITOR).on(DNS_UPTIME_EVENT.MONITOR_ID.eq(DNS_MONITOR.ID))
        .where(DNS_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .apply {
            if (monitorId != null) {
                and(DNS_MONITOR.ID.eq(monitorId))
            } else {
                and(DNS_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(DNS_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.sslIncidentsSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates,
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
            and(incidentStatesCondition(SSL_EVENT.ENDED_AT, period, states))
        }

    fun getHttpUptimeIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)

        return dslContext
            .httpUptimeIncidentSelect(monitorId, period, includeResolved.toIncidentStates())
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
            .pushUptimeIncidentSelect(monitorId, period, includeResolved.toIncidentStates())
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    fun getIcmpUptimeIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)

        return dslContext
            .icmpUptimeIncidentSelect(monitorId, period, includeResolved.toIncidentStates())
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    fun getTcpUptimeIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)

        return dslContext
            .tcpUptimeIncidentSelect(monitorId, period, includeResolved.toIncidentStates())
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    fun getDockerUptimeIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)

        return dslContext
            .dockerUptimeIncidentSelect(monitorId, period, includeResolved.toIncidentStates())
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    fun getDnsUptimeIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)

        return dslContext
            .dnsUptimeIncidentSelect(monitorId, period, includeResolved.toIncidentStates())
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
            .sslIncidentsSelect(monitorId, period, includeResolved.toIncidentStates())
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    /** Which incidents are fetched: the ongoing ones, the resolved ones or all of them. */
    private enum class IncidentStates { ONGOING, RESOLVED, ALL }

    private fun Boolean.toIncidentStates() = if (this) IncidentStates.ALL else IncidentStates.ONGOING

    /**
     * The condition of the incidents in the given [states] that were open at any point during the [period], written
     * so that it can use the index of [endedAt], instead of scanning every event.
     */
    private fun incidentStatesCondition(
        endedAt: Field<OffsetDateTime>,
        period: Duration?,
        states: IncidentStates,
    ): Condition {
        val ongoing = endedAt.isNull
        val resolved = period
            ?.let { endedAt.greaterThan(getCurrentTimestamp().minus(it)) }
            ?: endedAt.isNotNull
        return when (states) {
            IncidentStates.ONGOING -> ongoing
            IncidentStates.RESOLVED -> resolved
            IncidentStates.ALL -> if (period == null) DSL.noCondition() else ongoing.or(resolved)
        }
    }
}
