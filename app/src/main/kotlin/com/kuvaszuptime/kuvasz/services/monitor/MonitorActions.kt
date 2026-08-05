package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.metrics.numericMonitorId
import com.kuvaszuptime.kuvasz.models.MonitorCannotBeDeletedException
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorDeleteEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.monitorId
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.repositories.monitorType
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
import org.jooq.DSLContext
import java.time.Duration

abstract class MonitorActions<R : MonitorRecord, D : MonitorDetailsDto>(
    private val dslContext: DSLContext,
    private val appConfig: AppConfig,
    private val statusPageRepository: StatusPageRepository,
    private val monitorRepository: MonitorRepository<R, D>,
    private val eventDispatcher: EventDispatcher,
    private val statCalculator: StatCalculator,
    protected val maintenanceWindowService: MaintenanceWindowService,
) {
    private val monitorType: MonitorType get() = monitorRepository.monitorType

    /**
     * Checks if it's safe to update the monitor's name or delete it at all from the status pages' perspective.
     * If the monitor is referenced by a status page that is not writable, then we cannot change its name or delete it,
     * to preserve referential integrity.
     */
    fun isMonitorChangeable(existingMonitor: R): Boolean =
        if (!appConfig.isStatusPageExternalWriteDisabled()) {
            true
        } else {
            val referencingStatusPages = statusPageRepository.getStatusPagesOfMonitor(existingMonitor.monitorId())
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

    /**
     * Looks up a monitor and calculates its historical uptime stats over the given period, then lets the caller
     * assemble the type specific statistics on top of them.
     */
    protected fun <T> withUptimeHistory(
        monitorId: Long,
        period: Duration,
        block: (monitor: R, uptimeHistory: HistoricalUptimeStatsDto) -> T,
    ): T =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                block(monitor, statCalculator.calculateHistoricalUptimeStats(monitorType, period, monitor.id))
            }

    /**
     * Collects the status page data of the enabled monitors, calculating everything that is common across the monitor
     * types, and letting the caller enrich the result with the type specific details.
     */
    protected fun <S : StatusPageMonitorDetailsDto> buildStatusPageData(
        period: Duration,
        monitorIds: List<MonitorID>?,
        buildDetails: (monitor: D, uptime: StatusPageUptimeData) -> S,
    ): List<S> {
        val monitorNames = monitorIds?.filter { it.type == monitorType }?.map { it.name }
        val enabledMonitors = monitorRepository.fetchAllWithDetails(enabled = true, monitorNames = monitorNames)
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(enabledMonitors.map { it.monitorId() })
        val overviewsByMonitor = statCalculator.calculateUptimeOverviews(
            monitorType = monitorType,
            period = period,
            monitorIds = enabledMonitors.map { it.id },
        )

        return enabledMonitors.map { monitor ->
            val overview = overviewsByMonitor.getValue(monitor.id)
            val uptimeData = StatusPageUptimeData(
                uptimeRatio = overview.uptimeRatio,
                uptimeStatusHistory = overview.statusHistory,
                inMaintenance = windowsByMonitor[monitor.monitorId()].orEmpty().any { it.active },
            )
            buildDetails(monitor, uptimeData)
        }
    }

    fun R?.orThrowNotFound(monitorId: Long): R = this ?: throw MonitorNotFoundException(monitorId)

    protected data class StatusPageUptimeData(
        val uptimeRatio: Double?,
        val uptimeStatusHistory: List<StatusHistoryDto>,
        val inMaintenance: Boolean,
    )
}
