package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import jakarta.inject.Singleton
import org.jooq.DSLContext

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
        } as R?
}
