package com.kuvaszuptime.kuvasz.services.check.docker

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.DockerMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DockerUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.isDownNow
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import com.kuvaszuptime.kuvasz.services.docker.DockerHost
import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import com.kuvaszuptime.kuvasz.services.docker.DockerStatsResult
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.services.docker.cpuUsagePercentDecimal
import com.kuvaszuptime.kuvasz.util.loggerFor
import jakarta.inject.Singleton

@Singleton
class DockerUptimeChecker(
    private val apiClient: DockerApiClient?,
    private val hostRegistry: DockerHostRegistry?,
    private val uptimeEventRepository: DockerUptimeEventRepository,
    private val metricsLogRepository: DockerMetricsLogRepository,
    private val databaseEventHandler: DatabaseEventHandler,
    private val eventDispatcher: EventDispatcher,
    private val pendingFailureRepository: PendingFailureRepository,
    private val monitorRepository: DockerMonitorRepository,
) {

    fun check(
        monitor: DockerMonitorRecord,
        doAfter: ((monitor: DockerMonitorRecord) -> Unit)? = null,
    ) {
        logger.debug(
            "Starting Docker check for monitor [${monitor.name}] on ${monitor.dockerHost}/${monitor.container}"
        )

        // Both beans are absent altogether when no docker-hosts are configured at all, which is the same failure
        // for a monitor as naming a host that was since removed from the config
        val host = hostRegistry?.get(monitor.dockerHost)
        if (host == null || apiClient == null) {
            val error = Messages.dockerHostNotConfigured(monitor.dockerHost)
            reportDown(
                monitor = monitor,
                outcome = DockerCheckOutcome.Down(
                    error,
                    latencyMs = null,
                    image = null,
                    restartCount = null,
                    containerCreatedAt = null,
                )
            )
        } else {
            val outcome = apiClient
                .inspectContainer(host, monitor.container, monitor.timeoutMs)
                .toCheckOutcome(monitor.dockerHost, monitor.container)

            // Sampled once and used twice: the metrics log keeps the history, the up event feeds the exporters
            val stats = sampleStats(apiClient, monitor, host, outcome)
            recordMetrics(monitor, outcome, stats)

            when (outcome) {
                is DockerCheckOutcome.Up -> reportUp(monitor, outcome, stats)
                is DockerCheckOutcome.Down -> reportDown(monitor, outcome)
            }
        }

        logger.debug("Docker uptime check for monitor [${monitor.name}] finished")
        if (doAfter != null) {
            monitorRepository.findById(monitor.id, null)?.let { upToDateMonitor ->
                logger.debug("Calling doAfter() hook on monitor with name [${upToDateMonitor.name}]")
                doAfter(upToDateMonitor)
            }
        }
    }

    /**
     * A daemon that could not be reached produced no round-trip to time, so there is nothing to record: the row
     * exists to carry a measurement, and a check that measured nothing would only write nulls.
     */
    private fun recordMetrics(
        monitor: DockerMonitorRecord,
        outcome: DockerCheckOutcome,
        stats: DockerContainerStats?,
    ) {
        if (!monitor.metricsHistoryEnabled) return
        val latencyMs = outcome.latencyMs ?: return

        metricsLogRepository.insertLog(
            monitorId = monitor.id,
            latencyMs = latencyMs,
            stats = stats,
            restartCount = outcome.restartCount,
            containerCreatedAt = outcome.containerCreatedAt,
        )
    }

    /**
     * Only a container the inspection found running is worth sampling: the endpoint answers for a stopped one too,
     * but with an empty cgroup, and the call costs the daemon's whole collection cycle either way.
     */
    private fun sampleStats(
        client: DockerApiClient,
        monitor: DockerMonitorRecord,
        host: DockerHost,
        outcome: DockerCheckOutcome,
    ): DockerContainerStats? {
        if (!monitor.metricsHistoryEnabled || outcome !is DockerCheckOutcome.Up) return null

        return when (val result = client.containerStats(host, monitor.container, monitor.timeoutMs)) {
            is DockerStatsResult.Measured -> result.stats
            is DockerStatsResult.Unavailable -> {
                logger.debug(
                    "Could not sample the container of the monitor [${monitor.name}]: ${result.reason}"
                )
                null
            }
        }
    }

    private fun reportUp(monitor: DockerMonitorRecord, outcome: DockerCheckOutcome.Up, stats: DockerContainerStats?) {
        val event = DockerMonitorUpEvent(
            monitor = monitor,
            previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitor.id),
            latencyInMs = outcome.latencyMs,
            image = outcome.image,
            cpuUsagePercent = stats?.cpuUsagePercentDecimal,
            memoryUsageBytes = stats?.memoryUsageBytes,
            restartCount = outcome.restartCount,
            containerCreatedAt = outcome.containerCreatedAt,
        )
        pendingFailureRepository.deleteByMonitorId(monitor.id)
        databaseEventHandler.handleUptimeMonitorEvent(event)
        eventDispatcher.dispatch(event)
        if (monitor.restartAlertEnabled) detectRestarts(event)
    }

    /**
     * Compares the restart count against the one the previous event recorded. Only an UP check alerts: restarts seen
     * while the monitor is DOWN are already covered by the DOWN notification, and the DOWN event absorbs them.
     *
     * The count is only comparable within the same container: a recreated one starts from zero, so a changed creation
     * time is stored silently. A manual restart resets the count to zero too, so a lower count means one happened
     * since the previous check, and every restart counted since then was done by the restart policy after it.
     */
    private fun detectRestarts(event: DockerMonitorUpEvent) {
        val previousCount = event.previousEvent?.restartCount ?: return
        val currentCount = event.restartCount ?: return
        val isSameContainer = event.previousEvent?.containerCreatedAt
            ?.let { previousCreatedAt -> event.containerCreatedAt?.isEqual(previousCreatedAt) } == true
        val baselineCount = if (currentCount < previousCount) 0 else previousCount

        if (isSameContainer && currentCount > baselineCount) {
            eventDispatcher.dispatch(
                DockerContainerRestartedEvent(
                    monitor = event.monitor,
                    previousRestartCount = baselineCount,
                    currentRestartCount = currentCount,
                )
            )
        }
    }

    private fun reportDown(monitor: DockerMonitorRecord, outcome: DockerCheckOutcome.Down) {
        val event = DockerMonitorDownEvent(
            monitor = monitor,
            error = outcome.error,
            previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitor.id),
            latencyInMs = outcome.latencyMs,
            image = outcome.image,
            restartCount = outcome.restartCount,
            containerCreatedAt = outcome.containerCreatedAt,
        )
        if (event.isDownNow(pendingFailureRepository)) {
            databaseEventHandler.handleUptimeMonitorEvent(event)
            eventDispatcher.dispatch(event)
        }
    }

    companion object {
        private val logger = loggerFor<DockerUptimeChecker>()
    }
}
