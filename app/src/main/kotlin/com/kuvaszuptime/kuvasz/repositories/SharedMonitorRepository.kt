package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.DnsMonitor.DNS_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DockerMonitor.DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMonitor.ICMP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.TcpMonitor.TCP_MONITOR
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import jakarta.inject.Singleton
import org.jooq.DSLContext
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
}
