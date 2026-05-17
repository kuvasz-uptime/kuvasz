package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.numericMonitorId
import com.kuvaszuptime.kuvasz.models.MonitorCannotBeDeletedException
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.events.MonitorDeleteEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.icmp.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
import org.jooq.DSLContext

abstract class MonitorActions<R : MonitorRecord>(
    private val dslContext: DSLContext,
    private val appConfig: AppConfig,
    private val statusPageRepository: StatusPageRepository,
    private val monitorRepository: MonitorRepository<R>,
    private val eventDispatcher: EventDispatcher,
) {
    /**
     * Checks if it's safe to update the monitor's name or delete it at all from the status pages' perspective.
     * If the monitor is referenced by a status page that is not writable, then we cannot change its name or delete it,
     * to preserve referential integrity.
     */
    fun isMonitorChangeable(existingMonitor: R): Boolean =
        if (!appConfig.isStatusPageExternalWriteDisabled()) {
            true
        } else {
            val referencingStatusPages = statusPageRepository.getStatusPagesOfMonitor(existingMonitor.getMonitorId())
            referencingStatusPages.isEmpty()
        }

    fun deleteMonitorById(monitorId: Long, afterDelete: (R) -> Unit = {}): Unit =
        dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            monitorRepository.findById(monitorId, txCtx).orThrowNotFound(monitorId).let { monitor ->
                if (!isMonitorChangeable(monitor)) {
                    throw MonitorCannotBeDeletedException(
                        "Monitor cannot be deleted because it is referenced by a read-only status page"
                    )
                }
                monitorRepository.deleteById(monitor.id, txCtx)
                eventDispatcher.dispatch(MonitorDeleteEvent(monitor.numericMonitorId()))
                afterDelete(monitor)
            }
        }

    fun R?.orThrowNotFound(monitorId: Long): R = this ?: throw MonitorNotFoundException(monitorId)

    private fun R.getMonitorId(): MonitorID = when (this) {
        is HttpMonitorRecord -> this.monitorId()
        is PushMonitorRecord -> this.monitorId()
        is IcmpMonitorRecord -> this.monitorId()
        else -> throw IllegalArgumentException("Unknown monitor record type: ${this::class.java}")
    }
}
