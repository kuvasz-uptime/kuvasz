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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Context
class CheckScheduler(
    @Named(TaskExecutors.SCHEDULED) private val taskScheduler: TaskScheduler,
    private val monitorRepository: MonitorRepository,
    private val uptimeChecker: UptimeChecker,
    private val sslChecker: SSLChecker,
    dispatcher: CoroutineDispatcher,
    private val lockRegistry: UptimeCheckLockRegistry,
) {
    private val coroutineExHandler = CoroutineExceptionHandler { _, ex ->
        logger.warn("Coroutine failed with ${ex::class.simpleName}: ${ex.message}")
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher + coroutineExHandler)

    private val scheduledChecks: MutableList<ScheduledCheck> = mutableListOf()

    @PostConstruct
    fun initialize() {
        monitorRepository.fetchByEnabled(true).forEach { createChecksForMonitor(it) }
    }

    fun getScheduledChecks() = scheduledChecks

    @Suppress("UnusedPrivateMember") // False-positive
    private fun ScheduledCheck.log(monitor: MonitorRecord) {
        val estimatedNextCheckEpoch = System.currentTimeMillis() + task.getDelay(TimeUnit.MILLISECONDS)
        val estimatedNextCheck =
            Instant.ofEpochMilli(estimatedNextCheckEpoch).atOffset(ZoneOffset.UTC)
        logger.info(
            "${checkType.name} check for \"${monitor.name}\" (${monitor.url}) has been set up successfully. " +
                "Next check will happen around: $estimatedNextCheck"
        )
    }

    private fun Throwable.log(checkType: CheckType, monitor: MonitorRecord) {
        logger.error("${checkType.name} check for \"${monitor.name}\" (${monitor.url}) cannot be set up: $message")
    }

    private fun scheduledUptimeCheckSuccessHandler(
        monitor: MonitorRecord,
        doAfter: (ScheduledFuture<*>) -> Unit = {},
    ): (ScheduledFuture<*>) -> SchedulingError? = { scheduledUptimeTask ->
        ScheduledCheck(checkType = CheckType.UPTIME, monitorId = monitor.id, task = scheduledUptimeTask)
            .also { scheduledChecks.add(it) }
            .also { it.log(monitor) }
        doAfter(scheduledUptimeTask)
        null
    }

    private fun scheduledUptimeCheckErrorHandler(
        monitor: MonitorRecord,
    ): (Throwable) -> SchedulingError? = { error ->
        error.log(CheckType.UPTIME, monitor)
        SchedulingError(error.message)
    }

    fun createChecksForMonitor(monitor: MonitorRecord): SchedulingError? =
        scheduleUptimeCheck(monitor, resync = false).fold(
            onSuccess = scheduledUptimeCheckSuccessHandler(monitor) { _ ->
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
            },
            onFailure = scheduledUptimeCheckErrorHandler(monitor)
        )

    fun removeChecksOfMonitor(monitor: MonitorRecord) {
        scheduledChecks.forEach { check ->
            if (check.monitorId == monitor.id) {
                check.task.cancel(false)
            }
        }
        scheduledChecks.removeAll { it.monitorId == monitor.id }
        logger.info("Checks for \"${monitor.name}\" (${monitor.url}) has been removed successfully")
    }

    private fun removeUptimeCheckOfMonitor(monitor: MonitorRecord) {
        scheduledChecks
            .filter { it.checkType == CheckType.UPTIME && it.monitorId == monitor.id }
            .forEach { check ->
                check.task.cancel(false)
                scheduledChecks.remove(check)
            }
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

    private fun scheduleUptimeCheck(
        monitor: MonitorRecord,
        resync: Boolean,
    ): Result<ScheduledFuture<*>> =
        runCatching {
            // Spreading the first checks a little bit to prevent flooding the HTTP Client after startup
            val effectiveInitialDelay = if (resync) {
                monitor.uptimeCheckInterval
            } else {
                (1..monitor.uptimeCheckInterval).random()
            }
            val period = monitor.uptimeCheckInterval.toDurationOfSeconds()

            taskScheduler.scheduleWithFixedDelay(effectiveInitialDelay.toDurationOfSeconds(), period) {
                scope.launch {
                    if (!lockRegistry.tryAcquire(monitor.id)) return@launch

                    @Suppress("TooGenericExceptionCaught")
                    try {
                        uptimeChecker.check(monitor) { checkedMonitor ->
                            // Re-applying the original check interval which acts like kind of a synchronization to
                            // minimize the chance of overlapping requests
                            if (checkedMonitor.enabled) reScheduleUptimeCheckForMonitor(checkedMonitor)
                        }
                    } catch (ex: Throwable) {
                        // Better to catch and swallow everything that wasn't caught before to prevent
                        // the accidental cancellation of the parent coroutine
                        logger.error(
                            "An unexpected error happened during the uptime check of a " +
                                "monitor (${monitor.name}): ${ex.message}"
                        )
                    } finally {
                        withContext(NonCancellable) {
                            lockRegistry.release(monitor.id)
                        }
                    }
                }
            }
        }

    private fun scheduleSSLCheck(monitor: MonitorRecord): Result<ScheduledFuture<*>> =
        runCatching {
            val initialDelay = Duration.ofSeconds(
                (SSL_CHECK_INITIAL_DELAY_MIN_SECONDS..SSL_CHECK_INITIAL_DELAY_MAX_SECONDS).random()
            )
            val period = Duration.ofDays(SSL_CHECK_PERIOD_DAYS)
            taskScheduler.scheduleWithFixedDelay(initialDelay, period) {
                sslChecker.check(monitor)
            }
        }

    // Re-schedules the uptime check for a monitor, removing the previous one and scheduling a new one with an initial
    // delay of the monitor's uptime check interval, to decrease the chance of overlapping checks
    private fun reScheduleUptimeCheckForMonitor(monitor: MonitorRecord): SchedulingError? {
        removeUptimeCheckOfMonitor(monitor)

        return scheduleUptimeCheck(monitor, resync = true).fold(
            onSuccess = scheduledUptimeCheckSuccessHandler(monitor),
            onFailure = scheduledUptimeCheckErrorHandler(monitor),
        )
    }

    companion object {
        private const val SSL_CHECK_INITIAL_DELAY_MIN_SECONDS = 60L
        private const val SSL_CHECK_INITIAL_DELAY_MAX_SECONDS = 300L
        private const val SSL_CHECK_PERIOD_DAYS = 1L
        private val logger = LoggerFactory.getLogger(CheckScheduler::class.java)
    }
}
