package com.kuvaszuptime.kuvasz.services.check

import com.kuvaszuptime.kuvasz.jooq.SchedulableMonitorRecord
import com.kuvaszuptime.kuvasz.models.SchedulingException
import com.kuvaszuptime.kuvasz.models.monitor.monitorId
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import com.kuvaszuptime.kuvasz.util.toDurationOfSeconds
import io.micronaut.scheduling.TaskScheduler
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

/**
 * The common ancestor of the check schedulers, taking care of the whole lifecycle of the periodic uptime checks:
 * scheduling them for the enabled monitors, running them in a non-overlapping manner, re-scheduling them after every
 * finished check, and cancelling them on shutdown.
 *
 * Subclasses only have to provide the parts that are really specific to the given monitor type, and can hook their
 * additional, non-uptime related checks (e.g. the SSL checks of the HTTP monitors) into the lifecycle.
 */
abstract class UptimeCheckScheduler<R : SchedulableMonitorRecord>(
    protected val taskScheduler: TaskScheduler,
    private val monitorRepository: MonitorRepository<R, *>,
    dispatcher: CoroutineDispatcher,
    private val lockRegistry: UptimeCheckLockRegistry,
    protected val maintenanceWindowService: MaintenanceWindowService,
) : MonitorCheckScheduler, AutoCloseable {

    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val coroutineExHandler = CoroutineExceptionHandler { _, ex ->
        logger.warn("Coroutine failed with ${ex::class.simpleName}: ${ex.message}")
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher + coroutineExHandler)

    private val scheduledUptimeChecks: ConcurrentHashMap<Long, ScheduledFuture<*>> = ConcurrentHashMap()

    // Can't be a simple val, because monitorType is not initialized yet when the base class is constructed
    private val checkTypeLabel: String
        get() = monitorType.identifier.uppercase()

    /**
     * The identifier of a monitor in the logs, e.g. "my-monitor" (https://example.com)
     */
    protected val R.label: String
        get() = "\"$name\" ($checkTarget)"

    /**
     * The target of the check (e.g. an URL or a host), only used to identify the monitor in the logs.
     */
    protected abstract val R.checkTarget: String

    /**
     * Runs the type specific uptime check of the given monitor.
     */
    protected abstract suspend fun runCheck(monitor: R, doAfter: (R) -> Unit)

    /**
     * Called after the uptime check of a monitor has been created (or re-created) successfully, to let the subclasses
     * schedule their additional checks, if they have any.
     */
    protected open fun scheduleAdditionalChecks(monitor: R) = Unit

    /**
     * Called when the checks of a single monitor are removed from the scheduler.
     */
    protected open fun cancelAdditionalChecks(monitor: R) = Unit

    /**
     * Called when every check is removed from the scheduler, and on shutdown as well.
     */
    protected open fun cancelAllAdditionalChecks() = Unit

    override fun initialize() {
        monitorRepository.fetchByEnabled(enabled = true).forEach { createChecksForMonitor(it) }
    }

    fun getScheduledUptimeChecks() = scheduledUptimeChecks.toMap()

    /**
     * (Re)Creates the checks of a monitor. Relevant when a monitor is created or updated.
     */
    fun createChecksForMonitor(monitor: R): SchedulingException? =
        scheduleUptimeCheck(monitor, resync = false).fold(
            onSuccess = successHandler(monitor, doAfter = { scheduleAdditionalChecks(monitor) }),
            onFailure = errorHandler(monitor),
        )

    /**
     * Removes the checks of a monitor from the scheduler. Relevant when a monitor is disabled or deleted.
     */
    fun removeChecksOfMonitor(monitor: R) {
        scheduledUptimeChecks[monitor.id].gracefulCancel()
        scheduledUptimeChecks.remove(monitor.id)
        cancelAdditionalChecks(monitor)
        logger.debug("$checkTypeLabel checks for ${monitor.label} have been removed successfully")
    }

    override fun removeAllChecks() {
        scheduledUptimeChecks.forEach { it.value.gracefulCancel() }
        scheduledUptimeChecks.clear()
        cancelAllAdditionalChecks()
    }

    /**
     * Calculates the time of the next uptime check of a given monitor.
     */
    fun getNextCheck(monitorId: Long): OffsetDateTime? = scheduledUptimeChecks[monitorId]?.getNextCheck()

    /**
     * Handles the success of a scheduled check: it cancels the previous one (just in case) and registers the new one.
     */
    private fun successHandler(
        monitor: R,
        doAfter: () -> Unit = {},
    ): (ScheduledFuture<*>) -> SchedulingException? = { scheduledTask ->
        scheduledUptimeChecks[monitor.id].gracefulCancel()
        scheduledUptimeChecks[monitor.id] = scheduledTask
        logger.debug(
            "$checkTypeLabel check for ${monitor.label} has been set up successfully. " +
                "Next check will happen around: ${scheduledTask.getNextCheck()}"
        )
        doAfter()
        null
    }

    private fun errorHandler(monitor: R): (Throwable) -> SchedulingException? = { error ->
        logger.error("$checkTypeLabel check for ${monitor.label} cannot be set up: ${error.message}")
        SchedulingException(error.message)
    }

    /**
     * Takes care of the actual scheduling of the uptime check
     */
    private fun scheduleUptimeCheck(monitor: R, resync: Boolean): Result<ScheduledFuture<*>> =
        runCatching {
            // Spreading the first checks a little bit to prevent flooding the targets right after startup
            val effectiveInitialDelay = if (resync) {
                monitor.uptimeCheckInterval
            } else {
                (1..monitor.uptimeCheckInterval).random()
            }
            val period = monitor.uptimeCheckInterval.toDurationOfSeconds()

            taskScheduler.scheduleWithFixedDelay(effectiveInitialDelay.toDurationOfSeconds(), period) {
                scope.launch { runScheduledCheck(monitor) }
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runScheduledCheck(monitor: R) {
        if (!lockRegistry.tryAcquire(monitor.id)) return

        try {
            // Skip the check entirely while the monitor is under maintenance
            if (maintenanceWindowService.isUnderMaintenance(monitor.monitorId())) {
                logger.debug("Skipping $checkTypeLabel check for \"${monitor.name}\": it is under maintenance")
                return
            }
            runCheck(monitor) { checkedMonitor ->
                // Re-applying the original check interval which acts like kind of a synchronization to
                // minimize the chance of overlapping requests
                if (checkedMonitor.enabled) reScheduleUptimeCheckForMonitor(checkedMonitor)
            }
        } catch (ex: Exception) {
            // Better to catch and swallow everything that wasn't caught before to prevent
            // the accidental cancellation of the parent coroutine
            logger.error(
                "An unexpected error happened during the $checkTypeLabel check of a " +
                    "monitor (${monitor.name}): ${ex.message}",
                ex,
            )
        } finally {
            lockRegistry.release(monitor.id)
        }
    }

    /**
     * Re-schedules the uptime check for a monitor, removing the previous one and scheduling a new one with an initial
     * delay of the monitor's uptime check interval, to decrease the chance of overlapping checks
     */
    private fun reScheduleUptimeCheckForMonitor(monitor: R): SchedulingException? =
        scheduleUptimeCheck(monitor, resync = true).fold(
            onSuccess = successHandler(monitor),
            onFailure = errorHandler(monitor),
        )

    @PreDestroy
    final override fun close() {
        cancelAllAdditionalChecks()
        initiateShutdown(scheduledUptimeChecks, lockRegistry)
    }
}
