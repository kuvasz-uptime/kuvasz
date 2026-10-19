package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.DnsMonitor.DNS_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DockerMonitor.DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMonitor.ICMP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.TcpMonitor.TCP_MONITOR
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.util.unionAll
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record3
import org.jooq.Select
import org.jooq.Table
import org.jooq.impl.DSL

@Singleton
class SharedMonitorRepository(private val dslContext: DSLContext) {

    /**
     * A generic getter for monitors by their numeric ID. Not fully typesafe because of the implicit cast at the end,
     * but if it's used correctly, no runtime surprises should happen, because the cast is just a formality then
     */
    fun <R : MonitorRecord> findById(monitorId: NumericMonitorID, ctx: DSLContext = dslContext): R? =
        when (monitorId.type) {
            MonitorType.HTTP_SSL -> {
                ctx.selectFrom(HTTP_MONITOR)
                    .where(HTTP_MONITOR.ID.eq(monitorId.id))
                    .fetchOne()
            }

            MonitorType.PUSH -> {
                ctx.selectFrom(PUSH_MONITOR)
                    .where(PUSH_MONITOR.ID.eq(monitorId.id))
                    .fetchOne()
            }

            MonitorType.ICMP -> {
                ctx.selectFrom(ICMP_MONITOR)
                    .where(ICMP_MONITOR.ID.eq(monitorId.id))
                    .fetchOne()
            }

            MonitorType.TCP -> {
                ctx.selectFrom(TCP_MONITOR)
                    .where(TCP_MONITOR.ID.eq(monitorId.id))
                    .fetchOne()
            }

            MonitorType.DNS -> {
                ctx.selectFrom(DNS_MONITOR)
                    .where(DNS_MONITOR.ID.eq(monitorId.id))
                    .fetchOne()
            }

            MonitorType.DOCKER -> {
                ctx.selectFrom(DOCKER_MONITOR)
                    .where(DOCKER_MONITOR.ID.eq(monitorId.id))
                    .fetchOne()
            }
        } as R?

    /** The number of the enabled monitors of every type together. */
    fun countEnabled(): Int {
        val countsByType = listOf(
            HTTP_MONITOR to HTTP_MONITOR.ENABLED,
            PUSH_MONITOR to PUSH_MONITOR.ENABLED,
            ICMP_MONITOR to ICMP_MONITOR.ENABLED,
            TCP_MONITOR to TCP_MONITOR.ENABLED,
            DNS_MONITOR to DNS_MONITOR.ENABLED,
            DOCKER_MONITOR to DOCKER_MONITOR.ENABLED,
        ).map { (table, enabled) -> DSL.field(DSL.selectCount().from(table).where(enabled.isTrue)) }

        return dslContext.select(countsByType.reduce { total, count -> total.plus(count) }).fetchSingle().value1()
    }

    /** The type and the name of every monitor, sorted by the name without regard to the case. */
    fun fetchMonitorIds(): List<MonitorID> {
        val monitors = monitorIdSelects(namesByType = null).unionAll().asTable("monitor")
        val name = monitors.field(NAME_FIELD, String::class.java)
        val type = monitors.field(TYPE_FIELD, String::class.java)

        return dslContext
            .select(type, name)
            .from(monitors)
            .orderBy(DSL.lower(name), name, type)
            .fetch { (type, name) -> MonitorID(MonitorType.valueOf(type), name) }
    }

    /** The numeric IDs of the given monitors, without the ones that don't exist. */
    fun fetchNumericIds(monitorIds: Collection<MonitorID>): Map<MonitorID, Long> {
        val selects = monitorIdSelects(namesByType = monitorIds.groupBy({ it.type }, { it.name }))
        if (selects.isEmpty()) return emptyMap()

        return selects.unionAll().fetch().associate { (type, name, id) ->
            MonitorID(MonitorType.valueOf(type), name) to id
        }
    }

    // Every type is selected only if no names are given, otherwise only the types that have any of them
    private fun monitorIdSelects(
        namesByType: Map<MonitorType, List<String>>?,
    ): List<Select<Record3<String, String, Long>>> = MONITOR_ID_COLUMNS.mapNotNull { columns ->
        val condition = if (namesByType == null) {
            DSL.noCondition()
        } else {
            namesByType[columns.type]?.let { columns.name.`in`(it) } ?: return@mapNotNull null
        }
        dslContext
            .select(
                DSL.inline(columns.type.name).`as`(TYPE_FIELD),
                columns.name.`as`(NAME_FIELD),
                columns.id.`as`(ID_FIELD),
            )
            .from(columns.table)
            .where(condition)
    }

    private class MonitorIdColumns(
        val type: MonitorType,
        val table: Table<*>,
        val id: Field<Long>,
        val name: Field<String>,
    )

    private companion object {
        const val TYPE_FIELD = "type"
        const val NAME_FIELD = "name"
        const val ID_FIELD = "id"

        val MONITOR_ID_COLUMNS = listOf(
            MonitorIdColumns(MonitorType.HTTP_SSL, HTTP_MONITOR, HTTP_MONITOR.ID, HTTP_MONITOR.NAME),
            MonitorIdColumns(MonitorType.PUSH, PUSH_MONITOR, PUSH_MONITOR.ID, PUSH_MONITOR.NAME),
            MonitorIdColumns(MonitorType.ICMP, ICMP_MONITOR, ICMP_MONITOR.ID, ICMP_MONITOR.NAME),
            MonitorIdColumns(MonitorType.TCP, TCP_MONITOR, TCP_MONITOR.ID, TCP_MONITOR.NAME),
            MonitorIdColumns(MonitorType.DNS, DNS_MONITOR, DNS_MONITOR.ID, DNS_MONITOR.NAME),
            MonitorIdColumns(MonitorType.DOCKER, DOCKER_MONITOR, DOCKER_MONITOR.ID, DOCKER_MONITOR.NAME),
        )
    }
}
