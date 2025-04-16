package com.kuvaszuptime.kuvasz.services

import arrow.core.Either
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.models.ScheduledCheck
import com.kuvaszuptime.kuvasz.models.SchedulingError
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.util.catchBlocking
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

    companion object {
        private const val SSL_CHECK_INITIAL_DELAY_MINUTES = 1L
        private const val SSL_CHECK_PERIOD_DAYS = 1L
        private val logger = LoggerFactory.getLogger(CheckScheduler::class.java)
    }

    private val scheduledChecks: MutableList<ScheduledCheck> = mutableListOf()

    @PostConstruct
    fun initialize() {
        monitorRepository.fetchByEnabled(true)
            .forEach { createChecksForMonitor(it) }
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
            { error ->
                error.log(CheckType.UPTIME, monitor)
                SchedulingError(error.message)
            },
            { scheduledUptimeTask ->
                ScheduledCheck(checkType = CheckType.UPTIME, monitorId = monitor.id, task = scheduledUptimeTask)
                    .also { scheduledChecks.add(it) }
                    .also { it.log(monitor) }

                if (monitor.sslCheckEnabled) {
                    scheduleSSLCheck(monitor).fold(
                        { error ->
                            error.log(CheckType.SSL, monitor)
                            SchedulingError(error.message)
                        },
                        { scheduledSSLTask ->
                            ScheduledCheck(checkType = CheckType.SSL, monitorId = monitor.id, task = scheduledSSLTask)
                                .also { scheduledChecks.add(it) }
                                .also { it.log(monitor) }
                        }
                    )
                }
                null
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

    private fun scheduleUptimeCheck(monitor: MonitorRecord): Either<Throwable, ScheduledFuture<*>> =
        Either.catchBlocking {
            val period = monitor.uptimeCheckInterval.toDurationOfSeconds()
            taskScheduler.scheduleAtFixedRate(period, period) {
                uptimeChecker.check(monitor)
            }
        }

    private fun scheduleSSLCheck(monitor: MonitorRecord): Either<Throwable, ScheduledFuture<*>> =
        Either.catchBlocking {
            val initialDelay = Duration.ofMinutes(SSL_CHECK_INITIAL_DELAY_MINUTES)
            val period = Duration.ofDays(SSL_CHECK_PERIOD_DAYS)
            taskScheduler.scheduleAtFixedRate(initialDelay, period) {
                sslChecker.check(monitor)
            }
        }
}
