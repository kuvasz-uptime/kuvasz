package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.MonitorConfig
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.models.SchedulingException
import com.kuvaszuptime.kuvasz.models.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.util.toDurationOfSeconds
import com.kuvaszuptime.kuvasz.util.toOffsetDateTime
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
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
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
    private val yamlMonitorConfigs: List<MonitorConfig>,
    private val appConfig: AppConfig,
) {
    private val coroutineExHandler = CoroutineExceptionHandler { _, ex ->
        logger.warn("Coroutine failed with ${ex::class.simpleName}: ${ex.message}")
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher + coroutineExHandler)

    private val scheduledUptimeChecks: ConcurrentHashMap<Long, ScheduledFuture<*>> =
        ConcurrentHashMap()
    private val scheduledSSLChecks: ConcurrentHashMap<Long, ScheduledFuture<*>> =
        ConcurrentHashMap()

    private fun ScheduledFuture<*>?.gracefulCancel() {
        this?.cancel(false)
    }

    /**
     * Processes the YAML monitor configs. If any YAML config is found, it disables external modifications of monitors
     */
    private fun processYamlMonitorConfigs() {
        if (yamlMonitorConfigs.isNotEmpty()) {
            appConfig.disableExternalWrite()
            logger.info(
                "Disabled external modifications of monitors, because a YAML monitor config was found. " +
                    "Loading monitors from YAML config..."
            )
            val upsertedMonitorIds = yamlMonitorConfigs.map { yamlMonitor ->
                // Upserting the monitor from the YAML config
                monitorRepository.upsert(yamlMonitor.toMonitorRecord()).id
            }
            logger.info("Loaded ${yamlMonitorConfigs.size} monitors from YAML config")

            // Removing all monitors that are not in the YAML config
            val deletedCnt = monitorRepository.deleteAllExcept(ignoredIds = upsertedMonitorIds)
            logger.info("Deleted $deletedCnt monitors that were not in the YAML config")
        } else {
            logger.info(
                "No YAML monitor config was found. " +
                    "External modifications of monitors are enabled. Loading monitors from DB..."
            )
        }
    }

    @PostConstruct
    fun initialize() {
        // Parsing & processing the YAML monitor configs as they have a higher priority than the DB ones
        processYamlMonitorConfigs()

        // Scheduling the uptime checks for the enabled monitors
        monitorRepository.fetchByEnabled(true).forEach { createChecksForMonitor(it) }
    }

    fun getScheduledUptimeChecks() = scheduledUptimeChecks.toMap()
    fun getScheduledSSLChecks() = scheduledSSLChecks.toMap()

    private fun logCreated(monitor: MonitorRecord, checkType: CheckType, task: ScheduledFuture<*>) {
        val estimatedNextCheck = task.getNextCheck()
        logger.debug(
            "${checkType.name} check for \"${monitor.name}\" (${monitor.url}) has been set up successfully. " +
                "Next check will happen around: $estimatedNextCheck"
        )
    }

    private fun logError(monitor: MonitorRecord, checkType: CheckType, ex: Throwable) {
        logger.error("${checkType.name} check for \"${monitor.name}\" (${monitor.url}) cannot be set up: ${ex.message}")
    }

    private fun scheduledUptimeCheckSuccessHandler(monitor: MonitorRecord, doAfter: () -> Unit = {}) =
        scheduledCheckSuccessHandler(CheckType.UPTIME, monitor, doAfter)

    private fun scheduledSSLCheckSuccessHandler(monitor: MonitorRecord, doAfter: () -> Unit = {}) =
        scheduledCheckSuccessHandler(CheckType.SSL, monitor, doAfter)

    /**
     * Handles the success of a scheduled check. It cancels the previous check (just in case) and registers the new one.
     *
     * @param checkType The type of the check (UPTIME or SSL).
     * @param monitor The monitor for which the check was scheduled.
     * @param doAfter An optional callback to be executed after the check is successfully registered.
     */
    private fun scheduledCheckSuccessHandler(
        checkType: CheckType,
        monitor: MonitorRecord,
        doAfter: () -> Unit,
    ): (ScheduledFuture<*>) -> SchedulingException? = { scheduledUptimeTask ->
        monitor.cancelCheck(checkType)
        monitor.registerCheck(checkType, scheduledUptimeTask)
        logCreated(monitor, checkType, scheduledUptimeTask)
        doAfter()
        null
    }

    private fun MonitorRecord.registerCheck(checkType: CheckType, task: ScheduledFuture<*>) {
        when (checkType) {
            CheckType.UPTIME -> scheduledUptimeChecks[this.id] = task
            CheckType.SSL -> scheduledSSLChecks[this.id] = task
        }
    }

    private fun MonitorRecord.cancelCheck(checkType: CheckType) {
        when (checkType) {
            CheckType.UPTIME -> scheduledUptimeChecks[this.id].gracefulCancel()
            CheckType.SSL -> scheduledSSLChecks[this.id].gracefulCancel()
        }
    }

    private fun scheduledUptimeCheckErrorHandler(monitor: MonitorRecord) =
        scheduledCheckErrorHandler(CheckType.UPTIME, monitor)

    private fun scheduledSSLCheckErrorHandler(monitor: MonitorRecord) =
        scheduledCheckErrorHandler(CheckType.SSL, monitor)

    private fun scheduledCheckErrorHandler(
        checkType: CheckType,
        monitor: MonitorRecord,
    ): (Throwable) -> SchedulingException? = { error ->
        logError(monitor, checkType, error)
        SchedulingException(error.message)
    }

    /**
     * (Re)Creates the checks (uptime + SSL) of a monitor. Relevant when a monitor is created or updated.
     */
    fun createChecksForMonitor(monitor: MonitorRecord): SchedulingException? =
        scheduleUptimeCheck(monitor, resync = false).fold(
            onSuccess = scheduledUptimeCheckSuccessHandler(
                monitor,
                doAfter = {
                    // If the monitor is enabled, we need to take care of the SSL check as well
                    if (monitor.sslCheckEnabled) {
                        scheduleSSLCheck(monitor).fold(
                            onSuccess = scheduledSSLCheckSuccessHandler(monitor),
                            onFailure = scheduledSSLCheckErrorHandler(monitor)
                        )
                    }
                }
            ),
            onFailure = scheduledCheckErrorHandler(CheckType.UPTIME, monitor)
        )

    /**
     * Removes the checks (uptime + SSL) of a monitor from the scheduler.
     * Relevant when a monitor is disabled or deleted.
     */
    fun removeChecksOfMonitor(monitor: MonitorRecord) {
        monitor.cancelCheck(CheckType.UPTIME)
        scheduledUptimeChecks.remove(monitor.id)
        monitor.cancelCheck(CheckType.SSL)
        scheduledSSLChecks.remove(monitor.id)
        logger.info("Checks for \"${monitor.name}\" (${monitor.url}) has been removed successfully")
    }

    /**
     * Removes all the checks from the scheduler.
     */
    fun removeAllChecks() {
        scheduledUptimeChecks.forEach { it.value.gracefulCancel() }
        scheduledUptimeChecks.clear()
        scheduledSSLChecks.forEach { it.value.gracefulCancel() }
        scheduledSSLChecks.clear()
    }

    /**
     * Takes care of the actual scheduling of the uptime check
     */
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
                    } catch (ex: Exception) {
                        // Better to catch and swallow everything that wasn't caught before to prevent
                        // the accidental cancellation of the parent coroutine
                        logger.error(
                            "An unexpected error happened during the uptime check of a " +
                                "monitor (${monitor.name}): ${ex.message}",
                            ex,
                        )
                    } finally {
                        withContext(NonCancellable) {
                            lockRegistry.release(monitor.id)
                        }
                    }
                }
            }
        }

    /**
     * Takes care of the actual scheduling of the SSL check
     */
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

    /**
     * Re-schedules the uptime check for a monitor, removing the previous one and scheduling a new one with an initial
     * delay of the monitor's uptime check interval, to decrease the chance of overlapping checks
     */
    private fun reScheduleUptimeCheckForMonitor(monitor: MonitorRecord): SchedulingException? =
        scheduleUptimeCheck(monitor, resync = true).fold(
            onSuccess = scheduledUptimeCheckSuccessHandler(monitor),
            onFailure = scheduledUptimeCheckErrorHandler(monitor),
        )

    /**
     * Calculates the time of the next check for a given monitor and check type.
     */
    fun getNextCheck(checkType: CheckType, monitorId: Long): OffsetDateTime? {
        val scheduledTask = when (checkType) {
            CheckType.UPTIME -> scheduledUptimeChecks[monitorId]
            CheckType.SSL -> scheduledSSLChecks[monitorId]
        }
        return scheduledTask?.getNextCheck()
    }

    private fun ScheduledFuture<*>.getNextCheck(): OffsetDateTime {
        val nextCheckEpoch = System.currentTimeMillis() + this.getDelay(TimeUnit.MILLISECONDS)
        return Instant.ofEpochMilli(nextCheckEpoch).toOffsetDateTime()
    }

    companion object {
        private const val SSL_CHECK_INITIAL_DELAY_MIN_SECONDS = 60L
        private const val SSL_CHECK_INITIAL_DELAY_MAX_SECONDS = 300L
        private const val SSL_CHECK_PERIOD_DAYS = 1L
        private val logger = LoggerFactory.getLogger(CheckScheduler::class.java)
    }
}
