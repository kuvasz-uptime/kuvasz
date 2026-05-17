package com.kuvaszuptime.kuvasz.services.check.icmp

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.isDownNow
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
class IcmpUptimeChecker(
    private val pingExecutor: PingExecutor,
    private val uptimeEventRepository: IcmpUptimeEventRepository,
    private val latencyLogRepository: IcmpMetricsLogRepository,
    private val databaseEventHandler: DatabaseEventHandler,
    private val eventDispatcher: EventDispatcher,
    private val pendingFailureRepository: PendingFailureRepository,
    private val monitorRepository: IcmpMonitorRepository,
) {
    fun check(
        monitor: IcmpMonitorRecord,
        doAfter: ((monitor: IcmpMonitorRecord) -> Unit)? = null,
    ) {
        logger.debug("Starting ICMP check for monitor [${monitor.name}] on host: ${monitor.host}")

        @Suppress("TooGenericExceptionCaught")
        val pingResult = try {
            pingExecutor.execute(monitor.host, monitor.packetCount, monitor.timeoutSeconds)
        } catch (ex: Exception) {
            logger.error("Failed to execute ping for monitor [${monitor.name}]: ${ex.message}", ex)
            PingResult(
                packetsSent = monitor.packetCount,
                packetsReceived = 0,
                packetLossPercentage = 100,
                avgLatencyMs = null,
                rawOutput = ex.message ?: ex.javaClass.simpleName,
                isOutputRecognized = false,
            )
        }

        if (monitor.metricsHistoryEnabled) {
            latencyLogRepository.insertLog(
                monitorId = monitor.id,
                latencyMs = pingResult.avgLatencyMs,
                packetLossPercentage = pingResult.packetLossPercentage,
            )
        }

        pingResult.evaluate(monitor)

        logger.debug("ICMP uptime check for monitor [${monitor.name}] finished")
        if (doAfter != null) {
            monitorRepository.findById(monitor.id, null)?.let { upToDateMonitor ->
                logger.debug("Calling doAfter() hook on monitor with name [${upToDateMonitor.name}]")
                doAfter(upToDateMonitor)
            }
        }
    }

    private fun PingResult.evaluate(monitor: IcmpMonitorRecord) {
        val previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitor.id)

        if (packetLossPercentage >= monitor.packetLossThreshold) {
            val error = if (isOutputRecognized) {
                Messages.icmpPacketLossError(
                    packetLossPercentage,
                    packetsSent,
                    packetsReceived,
                )
            } else {
                rawOutput
            }
            val event = IcmpMonitorDownEvent(
                monitor = monitor,
                error = error,
                previousEvent = previousEvent,
                packetLossPercentage = packetLossPercentage,
            )
            if (event.isDownNow(pendingFailureRepository)) {
                databaseEventHandler.handleUptimeMonitorEvent(event)
                eventDispatcher.dispatch(event)
            }
        } else {
            val event = IcmpMonitorUpEvent(
                monitor = monitor,
                previousEvent = previousEvent,
                latencyInMs = avgLatencyMs,
                packetLossPercentage = packetLossPercentage,
            )
            pendingFailureRepository.deleteByMonitorId(monitor.id)
            databaseEventHandler.handleUptimeMonitorEvent(event)
            eventDispatcher.dispatch(event)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IcmpUptimeChecker::class.java)
    }
}
