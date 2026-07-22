package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.SchedulingException
import com.kuvaszuptime.kuvasz.models.monitor.dns.monitorId
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.getNextCheck
import com.kuvaszuptime.kuvasz.services.check.gracefulCancel
import com.kuvaszuptime.kuvasz.services.check.initiateShutdown
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.util.toDurationOfSeconds
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Singleton
class DnsCheckScheduler(
    @param:Named(TaskExecutors.SCHEDULED) private val taskScheduler: TaskScheduler,
    private val monitorRepository: DnsMonitorRepository,
    private val uptimeChecker: DnsUptimeChecker,
    dispatcher: CoroutineDispatcher,
    private val lockRegistry: UptimeCheckLockRegistry,
    private val maintenanceWindowService: MaintenanceWindowService,
) : AutoCloseable {
    private val coroutineExHandler = CoroutineExceptionHandler { _, ex ->
        logger.warn("Coroutine failed with ${ex::class.simpleName}: ${ex.message}")
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher + coroutineExHandler)

    private val scheduledUptimeChecks: ConcurrentHashMap<Long, ScheduledFuture<*>> = ConcurrentHashMap()

    fun initialize() {
        monitorRepository.fetchByEnabled(enabled = true).forEach { createChecksForMonitor(it) }
    }

    fun getScheduledUptimeChecks() = scheduledUptimeChecks.toMap()

    fun createChecksForMonitor(monitor: DnsMonitorRecord): SchedulingException? =
        scheduleUptimeCheck(monitor, resync = false).fold(
            onSuccess = { scheduledTask ->
                scheduledUptimeChecks[monitor.id].gracefulCancel()
                scheduledUptimeChecks[monitor.id] = scheduledTask
                val estimatedNextCheck = scheduledTask.getNextCheck()
                logger.debug(
                    "DNS check for \"${monitor.name}\" (${monitor.host}) scheduled. " +
                        "Next check around: $estimatedNextCheck"
                )
                null
            },
            onFailure = { error ->
                logger.error(
                    "DNS check for \"${monitor.name}\" (${monitor.host}) cannot be set up: " +
                        error.message
                )
                SchedulingException(error.message)
            }
        )

    fun removeChecksOfMonitor(monitor: DnsMonitorRecord) {
        scheduledUptimeChecks[monitor.id].gracefulCancel()
        scheduledUptimeChecks.remove(monitor.id)
        logger.debug("DNS checks for \"${monitor.name}\" (${monitor.host}) removed successfully")
    }

    fun removeAllChecks() {
        scheduledUptimeChecks.forEach { it.value.gracefulCancel() }
        scheduledUptimeChecks.clear()
    }

    private fun scheduleUptimeCheck(monitor: DnsMonitorRecord, resync: Boolean): Result<ScheduledFuture<*>> =
        runCatching {
            // Spreading the first checks a little bit to prevent flooding after startup
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
                        // Skip the check entirely while the monitor is under maintenance.
                        if (maintenanceWindowService.isUnderMaintenance(monitor.monitorId())) {
                            logger.debug("Skipping DNS check for \"${monitor.name}\": it is under maintenance")
                            return@launch
                        }
                        uptimeChecker.check(monitor) { checkedMonitor ->
                            // Re-applying the original check interval which acts like kind of a synchronization to
                            // minimize the chance of overlapping requests
                            if (checkedMonitor.enabled) reScheduleUptimeCheckForMonitor(checkedMonitor)
                        }
                    } catch (ex: Exception) {
                        // Better to catch and swallow everything that wasn't caught before to prevent
                        // the accidental cancellation of the parent coroutine
                        logger.error(
                            "An unexpected error happened during the DNS check of monitor " +
                                "(${monitor.name}): ${ex.message}",
                            ex,
                        )
                    } finally {
                        lockRegistry.release(monitor.id)
                    }
                }
            }
        }

    private fun reScheduleUptimeCheckForMonitor(monitor: DnsMonitorRecord): SchedulingException? =
        scheduleUptimeCheck(monitor, resync = true).fold(
            onSuccess = { scheduledTask ->
                scheduledUptimeChecks[monitor.id].gracefulCancel()
                scheduledUptimeChecks[monitor.id] = scheduledTask
                null
            },
            onFailure = { error ->
                logger.error("DNS reschedule for \"${monitor.name}\" failed: ${error.message}")
                SchedulingException(error.message)
            }
        )

    fun getNextCheck(monitorId: Long): OffsetDateTime? =
        scheduledUptimeChecks[monitorId]?.getNextCheck()

    @PreDestroy
    override fun close() {
        initiateShutdown(scheduledUptimeChecks, lockRegistry)
    }

    companion object {
        private val logger = loggerFor<DnsCheckScheduler>()
    }
}
