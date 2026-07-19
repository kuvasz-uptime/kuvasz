package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.isDownNow
import com.kuvaszuptime.kuvasz.util.loggerFor
import jakarta.inject.Singleton

@Singleton
class TcpUptimeChecker(
    private val connectExecutor: TcpConnectExecutor,
    private val uptimeEventRepository: TcpUptimeEventRepository,
    private val metricsLogRepository: TcpMetricsLogRepository,
    private val databaseEventHandler: DatabaseEventHandler,
    private val eventDispatcher: EventDispatcher,
    private val pendingFailureRepository: PendingFailureRepository,
    private val monitorRepository: TcpMonitorRepository,
) {
    fun check(
        monitor: TcpMonitorRecord,
        doAfter: ((monitor: TcpMonitorRecord) -> Unit)? = null,
    ) {
        logger.debug("Starting TCP check for monitor [${monitor.name}] on ${monitor.host}:${monitor.port}")

        val checkResult = connectExecutor.execute(monitor.host, monitor.port, monitor.timeoutMs)

        if (monitor.metricsHistoryEnabled) {
            metricsLogRepository.insertLog(
                monitorId = monitor.id,
                latencyMs = checkResult.latencyMs,
            )
        }

        checkResult.evaluate(monitor)

        logger.debug("TCP uptime check for monitor [${monitor.name}] finished")
        if (doAfter != null) {
            monitorRepository.findById(monitor.id, null)?.let { upToDateMonitor ->
                logger.debug("Calling doAfter() hook on monitor with name [${upToDateMonitor.name}]")
                doAfter(upToDateMonitor)
            }
        }
    }

    private fun TcpCheckResult.evaluate(monitor: TcpMonitorRecord) {
        val previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
        val latencyThreshold = monitor.latencyThresholdMs
        val latencyExceeded = isConnected
            && latencyThreshold != null
            && latencyMs != null
            && latencyMs > latencyThreshold

        if (!isConnected || latencyExceeded) {
            val errorMessage = if (latencyExceeded) {
                Messages.tcpLatencyThresholdError(latencyMs.toString(), latencyThreshold.toString())
            } else {
                error ?: Messages.yourTcpMonitorIsDown(monitor.name)
            }
            val event = TcpMonitorDownEvent(
                monitor = monitor,
                error = errorMessage,
                previousEvent = previousEvent,
                latencyInMs = latencyMs,
            )
            if (event.isDownNow(pendingFailureRepository)) {
                databaseEventHandler.handleUptimeMonitorEvent(event)
                eventDispatcher.dispatch(event)
            }
        } else {
            val event = TcpMonitorUpEvent(
                monitor = monitor,
                previousEvent = previousEvent,
                latencyInMs = latencyMs,
            )
            pendingFailureRepository.deleteByMonitorId(monitor.id)
            databaseEventHandler.handleUptimeMonitorEvent(event)
            eventDispatcher.dispatch(event)
        }
    }

    companion object {
        private val logger = loggerFor<TcpUptimeChecker>()
    }
}
