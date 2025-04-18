package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.models.ScheduledCheck
import com.kuvaszuptime.kuvasz.models.SchedulingError
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.util.toDurationOfSeconds
import io.micronaut.context.annotation.Context
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.annotation.PostConstruct
import jakarta.inject.Named
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ScheduledFuture

@Context
class CheckScheduler(
    @Named(TaskExecutors.SCHEDULED) private val taskScheduler: TaskScheduler,
    private val monitorRepository: MonitorRepository,
    private val uptimeChecker: UptimeChecker,
    private val sslChecker: SSLChecker
) {

    private val scheduledChecks: MutableList<ScheduledCheck> = mutableListOf()

    @PostConstruct
    fun initialize() {
        monitorRepository.fetchByEnabled(true).forEach { createChecksForMonitor(it) }
    }

    fun getScheduledChecks() = scheduledChecks

    fun createChecksForMonitor(monitor: MonitorRecord): SchedulingError? {
        fun Throwable.log(checkType: CheckType, monitor: MonitorRecord) {
            logger.error("${checkType.name} check for \"${monitor.name}\" (${monitor.url}) cannot be set up: $message")
        }

        fun ScheduledCheck.log(monitor: MonitorRecord) {
            logger.info("${checkType.name} check for \"${monitor.name}\" (${monitor.url}) has been set up successfully")
        }

        return scheduleUptimeCheck(monitor).fold(
            { scheduledUptimeTask ->
                ScheduledCheck(checkType = CheckType.UPTIME, monitorId = monitor.id, task = scheduledUptimeTask)
                    .also { scheduledChecks.add(it) }
                    .also { it.log(monitor) }

                if (monitor.sslCheckEnabled) {
                    scheduleSSLCheck(monitor).fold(
                        { scheduledSSLTask ->
                            ScheduledCheck(checkType = CheckType.SSL, monitorId = monitor.id, task = scheduledSSLTask)
                                .also { scheduledChecks.add(it) }
                                .also { it.log(monitor) }
                        },
                        { error ->
                            error.log(CheckType.SSL, monitor)
                            SchedulingError(error.message)
                        }
                    )
                }
                null
            },
            { error ->
                error.log(CheckType.UPTIME, monitor)
                SchedulingError(error.message)
            }
        )
    }

    fun removeChecksOfMonitor(monitor: MonitorRecord) {
        scheduledChecks.forEach { check ->
            if (check.monitorId == monitor.id) {
                check.task.cancel(false)
            }
        }
        scheduledChecks.removeAll { it.monitorId == monitor.id }
        logger.info("Uptime check for \"${monitor.name}\" (${monitor.url}) has been removed successfully")
    }

    fun removeAllChecks() {
        scheduledChecks.forEach { check ->
            check.task.cancel(false)
        }
        scheduledChecks.clear()
    }

    fun updateChecksForMonitor(
        existingMonitor: MonitorRecord,
        updatedMonitor: MonitorRecord
    ): SchedulingError? {
        removeChecksOfMonitor(existingMonitor)
        return createChecksForMonitor(updatedMonitor)
    }

    private fun scheduleUptimeCheck(monitor: MonitorRecord): Result<ScheduledFuture<*>> =
        runCatching {
            // Spreading the first checks a little bit to prevent flooding the HTTP Client after startup
            val initialDelay = (1..monitor.uptimeCheckInterval).random().toDurationOfSeconds()
            val period = monitor.uptimeCheckInterval.toDurationOfSeconds()
            taskScheduler.scheduleWithFixedDelay(initialDelay, period) {
                uptimeChecker.check(monitor)
            }
        }

    private fun scheduleSSLCheck(monitor: MonitorRecord): Result<ScheduledFuture<*>> =
        runCatching {
            val initialDelay = Duration.ofMinutes(SSL_CHECK_INITIAL_DELAY_MINUTES)
            val period = Duration.ofDays(SSL_CHECK_PERIOD_DAYS)
            taskScheduler.scheduleWithFixedDelay(initialDelay, period) {
                sslChecker.check(monitor)
            }
        }

    companion object {
        private const val SSL_CHECK_INITIAL_DELAY_MINUTES = 1L
        private const val SSL_CHECK_PERIOD_DAYS = 1L
        private val logger = LoggerFactory.getLogger(CheckScheduler::class.java)
    }
}
