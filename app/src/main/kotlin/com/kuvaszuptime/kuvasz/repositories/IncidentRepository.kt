package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.Tables.SSL_EVENT
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DnsMonitor.DNS_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DnsUptimeEvent.DNS_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.DockerMonitor.DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DockerUptimeEvent.DOCKER_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.HttpUptimeEvent.HTTP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMonitor.ICMP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpUptimeEvent.ICMP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushUptimeEvent.PUSH_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.TcpMonitor.TCP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.TcpUptimeEvent.TCP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentSortField
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.util.fetchPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.sortedIn
import com.kuvaszuptime.kuvasz.util.unionAll
import jakarta.inject.Singleton
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.kotlin.and
import java.time.Duration
import java.time.OffsetDateTime

@Suppress("TooManyFunctions")
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
     * @param types Optional types of the incidents to return, every type if null.
     *
     * @return List of [IncidentDto] matching the criteria.
     */
    fun getIncidents(
        monitorId: Long? = null,
        period: Duration? = null,
        includeResolved: Boolean,
        includeSslIncidents: Boolean = true,
        types: Set<IncidentType>? = null,
    ): List<IncidentDto> {
        val orderFieldName = DSL.name(IncidentDto::updatedAt.name)
        val selects = incidentSelects(monitorId, period, includeResolved.toIncidentStates(), includeSslIncidents, types)
        if (selects.isEmpty()) return emptyList()
        val incidents = selects.unionAll().asTable("incident")

        return dslContext
            .selectFrom(incidents)
            .orderBy(DSL.field(orderFieldName).desc())
            .fetchInto(IncidentDto::class.java)
    }

    /**
     * Fetches a page of the incidents, the same ones [getIncidents] would return, optionally narrowed to a single
     * [type] (selected on its own, without the UNION ALL of every type) and to the monitors whose name contains [name].
     * The incidents with the same value are sorted by their type and the ID of their event, which identify them.
     */
    fun getIncidentsPage(
        monitorId: Long?,
        period: Duration,
        includeResolved: Boolean,
        type: IncidentType?,
        name: String?,
        sorting: Sorting<IncidentSortField>,
        pageRequest: PageRequest,
    ): PagedResult<IncidentDto> {
        val incidents = incidentSelects(
            monitorId = monitorId,
            period = period,
            states = includeResolved.toIncidentStates(),
            includeSslIncidents = true,
            types = type?.let { setOf(it) },
        ).unionAll().asTable("incident")
        val condition = containsIgnoreCaseCondition(incidents.incidentField(IncidentDto::monitorName.name), name)

        return dslContext
            .selectFrom(incidents)
            .where(condition)
            .fetchPage(
                countQuery = dslContext.selectCount().from(incidents).where(condition),
                pageRequest = pageRequest,
                orderBy = listOf(
                    sorting.field.toField(incidents).sortedIn(sorting.order),
                    incidents.incidentField(IncidentDto::incidentType.name).asc(),
                    checkNotNull(incidents.field(IncidentDto::eventId.name)).asc(),
                ),
                type = IncidentDto::class.java,
            )
    }

    private fun Table<*>.incidentField(name: String): Field<String> = checkNotNull(field(name, String::class.java))

    private fun IncidentSortField.toField(incidents: Table<*>): Field<*> = checkNotNull(
        when (this) {
            IncidentSortField.ID -> incidents.field(IncidentDto::eventId.name)
            IncidentSortField.MONITOR_NAME -> incidents.field(IncidentDto::monitorName.name)
            IncidentSortField.INCIDENT_TYPE -> incidents.field(IncidentDto::incidentType.name)
            IncidentSortField.STATUS -> incidents.field(IncidentDto::status.name)
            IncidentSortField.STARTED_AT -> incidents.field(IncidentDto::startedAt.name)
            IncidentSortField.ENDED_AT -> incidents.field(IncidentDto::endedAt.name)
            IncidentSortField.UPDATED_AT -> incidents.field(IncidentDto::updatedAt.name)
        }
    )

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

    /**
     * Fetches a single incident by its type and the ID of its event, regardless of its monitor being enabled, unlike
     * the lists. Returns null if there is no such event, or it's not an incident (e.g. an UP event).
     */
    fun getIncident(incidentType: IncidentType, eventId: Long): IncidentDto? =
        dslContext.incidentSelect(
            incidentType = incidentType,
            monitorId = null,
            period = null,
            states = IncidentStates.ALL,
            eventId = eventId,
        ).fetchOneInto(IncidentDto::class.java)

    /**
     * The integrations that were notified when the [incident] started, and when it was resolved. The latter are
     * recorded on the event that ended the incident, which started at the very same time.
     */
    fun getNotifiedIntegrations(incident: IncidentDto): NotifiedIntegrations {
        val table = incidentEventTables.getValue(incident.incidentType)
        val resolvingEvent = incident.endedAt?.let { endedAt ->
            table.monitorId.eq(incident.monitorId).and(table.startedAt.eq(endedAt))
        }
        val integrationsByEventId = dslContext
            .select(table.id, table.integrations)
            .from(table.events)
            .where(DSL.or(listOfNotNull(table.id.eq(incident.eventId), resolvingEvent)))
            .fetchMap(table.id, table.integrations)

        return NotifiedIntegrations(
            started = integrationsByEventId[incident.eventId].orEmpty().toList(),
            resolved = incident.endedAt?.let {
                integrationsByEventId.filterKeys { eventId -> eventId != incident.eventId }
                    .values
                    .firstOrNull()
                    .orEmpty()
                    .toList()
            },
        )
    }

    /**
     * Finds the ID of the event of the monitor that started at the given time, i.e. the event that was persisted from
     * a just dispatched monitor event. For SSL, it's any SSL event, not only the invalid ones (the incidents).
     */
    fun findEventIdStartedAt(incidentType: IncidentType, monitorId: Long, startedAt: OffsetDateTime): Long? {
        val table = incidentEventTables.getValue(incidentType)

        return dslContext
            .select(table.id)
            .from(table.events)
            .where(table.monitorId.eq(monitorId))
            .and(table.startedAt.eq(startedAt))
            .limit(1)
            .fetchOne(table.id)
    }

    private fun DSLContext.incidentSelect(
        incidentType: IncidentType,
        monitorId: Long?,
        period: Duration?,
        states: IncidentStates,
        eventId: Long? = null,
    ) = when (incidentType) {
        IncidentType.HTTP -> httpUptimeIncidentSelect(monitorId, period, states, eventId)
        IncidentType.SSL -> sslIncidentsSelect(monitorId, period, states, eventId)
        IncidentType.PUSH -> pushUptimeIncidentSelect(monitorId, period, states, eventId)
        IncidentType.ICMP -> icmpUptimeIncidentSelect(monitorId, period, states, eventId)
        IncidentType.TCP -> tcpUptimeIncidentSelect(monitorId, period, states, eventId)
        IncidentType.DNS -> dnsUptimeIncidentSelect(monitorId, period, states, eventId)
        IncidentType.DOCKER -> dockerUptimeIncidentSelect(monitorId, period, states, eventId)
    }

    /** The incidents of every requested type, each of them selected on its own. */
    private fun incidentSelects(
        monitorId: Long?,
        period: Duration?,
        states: IncidentStates,
        includeSslIncidents: Boolean,
        types: Set<IncidentType>? = null,
    ) = IncidentType.entries
        .filter { type -> types == null || type in types }
        .filter { type -> includeSslIncidents || type != IncidentType.SSL }
        .map { type -> dslContext.incidentSelect(type, monitorId, period, states) }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.httpUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates,
        eventId: Long? = null,
    ) = this
        .select(
            HTTP_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            HTTP_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            HTTP_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.HTTP.name).`as`(IncidentDto::incidentType.name),
            HTTP_UPTIME_EVENT.ID.`as`(IncidentDto::eventId.name),
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
            when {
                eventId != null -> and(HTTP_UPTIME_EVENT.ID.eq(eventId))
                monitorId != null -> and(HTTP_MONITOR.ID.eq(monitorId))
                else -> and(HTTP_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(HTTP_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.pushUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates,
        eventId: Long? = null,
    ) = this
        .select(
            PUSH_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            PUSH_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            PUSH_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.PUSH.name).`as`(IncidentDto::incidentType.name),
            PUSH_UPTIME_EVENT.ID.`as`(IncidentDto::eventId.name),
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
            when {
                eventId != null -> and(PUSH_UPTIME_EVENT.ID.eq(eventId))
                monitorId != null -> and(PUSH_MONITOR.ID.eq(monitorId))
                else -> and(PUSH_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(PUSH_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.icmpUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates,
        eventId: Long? = null,
    ) = this
        .select(
            ICMP_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            ICMP_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            ICMP_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.ICMP.name).`as`(IncidentDto::incidentType.name),
            ICMP_UPTIME_EVENT.ID.`as`(IncidentDto::eventId.name),
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
            when {
                eventId != null -> and(ICMP_UPTIME_EVENT.ID.eq(eventId))
                monitorId != null -> and(ICMP_MONITOR.ID.eq(monitorId))
                else -> and(ICMP_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(ICMP_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.tcpUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates,
        eventId: Long? = null,
    ) = this
        .select(
            TCP_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            TCP_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            TCP_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.TCP.name).`as`(IncidentDto::incidentType.name),
            TCP_UPTIME_EVENT.ID.`as`(IncidentDto::eventId.name),
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
            when {
                eventId != null -> and(TCP_UPTIME_EVENT.ID.eq(eventId))
                monitorId != null -> and(TCP_MONITOR.ID.eq(monitorId))
                else -> and(TCP_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(TCP_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.dockerUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates,
        eventId: Long? = null,
    ) = this
        .select(
            DOCKER_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            DOCKER_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            DOCKER_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.DOCKER.name).`as`(IncidentDto::incidentType.name),
            DOCKER_UPTIME_EVENT.ID.`as`(IncidentDto::eventId.name),
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
            when {
                eventId != null -> and(DOCKER_UPTIME_EVENT.ID.eq(eventId))
                monitorId != null -> and(DOCKER_MONITOR.ID.eq(monitorId))
                else -> and(DOCKER_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(DOCKER_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.dnsUptimeIncidentSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates,
        eventId: Long? = null,
    ) = this
        .select(
            DNS_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            DNS_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            DNS_MONITOR.ENABLED.`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.DNS.name).`as`(IncidentDto::incidentType.name),
            DNS_UPTIME_EVENT.ID.`as`(IncidentDto::eventId.name),
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
            when {
                eventId != null -> and(DNS_UPTIME_EVENT.ID.eq(eventId))
                monitorId != null -> and(DNS_MONITOR.ID.eq(monitorId))
                else -> and(DNS_MONITOR.ENABLED.isTrue)
            }
            and(incidentStatesCondition(DNS_UPTIME_EVENT.ENDED_AT, period, states))
        }

    @Suppress("IgnoredReturnValue")
    private fun DSLContext.sslIncidentsSelect(
        monitorId: Long? = null,
        period: Duration? = null,
        states: IncidentStates,
        eventId: Long? = null,
    ) = this
        .select(
            HTTP_MONITOR.ID.`as`(IncidentDto::monitorId.name),
            HTTP_MONITOR.NAME.`as`(IncidentDto::monitorName.name),
            DSL.field(HTTP_MONITOR.ENABLED.and(HTTP_MONITOR.SSL_CHECK_ENABLED))
                .`as`(IncidentDto::isMonitorEnabled.name),
            DSL.inline(IncidentType.SSL.name).`as`(IncidentDto::incidentType.name),
            SSL_EVENT.ID.`as`(IncidentDto::eventId.name),
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
            when {
                eventId != null -> and(SSL_EVENT.ID.eq(eventId))
                monitorId != null -> and(HTTP_MONITOR.ID.eq(monitorId))
                else -> and(HTTP_MONITOR.ENABLED.isTrue).and(HTTP_MONITOR.SSL_CHECK_ENABLED.isTrue)
            }
            and(incidentStatesCondition(SSL_EVENT.ENDED_AT, period, states))
        }

    private class IncidentEventTable(
        val id: TableField<*, Long>,
        val monitorId: TableField<*, Long>,
        val startedAt: TableField<*, OffsetDateTime>,
        val integrations: TableField<*, Array<IntegrationID>>,
    ) {
        val events: Table<*> = checkNotNull(id.table)
    }

    private val incidentEventTables = IncidentType.entries.associateWith { type ->
        when (type) {
            IncidentType.HTTP -> IncidentEventTable(
                HTTP_UPTIME_EVENT.ID,
                HTTP_UPTIME_EVENT.MONITOR_ID,
                HTTP_UPTIME_EVENT.STARTED_AT,
                HTTP_UPTIME_EVENT.INTEGRATIONS,
            )

            IncidentType.SSL -> IncidentEventTable(
                SSL_EVENT.ID,
                SSL_EVENT.MONITOR_ID,
                SSL_EVENT.STARTED_AT,
                SSL_EVENT.INTEGRATIONS,
            )

            IncidentType.PUSH -> IncidentEventTable(
                PUSH_UPTIME_EVENT.ID,
                PUSH_UPTIME_EVENT.MONITOR_ID,
                PUSH_UPTIME_EVENT.STARTED_AT,
                PUSH_UPTIME_EVENT.INTEGRATIONS,
            )

            IncidentType.ICMP -> IncidentEventTable(
                ICMP_UPTIME_EVENT.ID,
                ICMP_UPTIME_EVENT.MONITOR_ID,
                ICMP_UPTIME_EVENT.STARTED_AT,
                ICMP_UPTIME_EVENT.INTEGRATIONS,
            )

            IncidentType.TCP -> IncidentEventTable(
                TCP_UPTIME_EVENT.ID,
                TCP_UPTIME_EVENT.MONITOR_ID,
                TCP_UPTIME_EVENT.STARTED_AT,
                TCP_UPTIME_EVENT.INTEGRATIONS,
            )

            IncidentType.DNS -> IncidentEventTable(
                DNS_UPTIME_EVENT.ID,
                DNS_UPTIME_EVENT.MONITOR_ID,
                DNS_UPTIME_EVENT.STARTED_AT,
                DNS_UPTIME_EVENT.INTEGRATIONS,
            )

            IncidentType.DOCKER -> IncidentEventTable(
                DOCKER_UPTIME_EVENT.ID,
                DOCKER_UPTIME_EVENT.MONITOR_ID,
                DOCKER_UPTIME_EVENT.STARTED_AT,
                DOCKER_UPTIME_EVENT.INTEGRATIONS,
            )
        }
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

data class NotifiedIntegrations(
    val started: List<IntegrationID>,
    val resolved: List<IntegrationID>?,
)
