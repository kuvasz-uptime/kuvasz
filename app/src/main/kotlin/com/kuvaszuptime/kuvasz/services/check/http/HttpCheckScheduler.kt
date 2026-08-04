package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.http.safeDisplayUrl
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.getNextCheck
import com.kuvaszuptime.kuvasz.services.check.gracefulCancel
import com.kuvaszuptime.kuvasz.services.check.ssl.SSLChecker
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

/**
 * Beyond the uptime checks handled by [UptimeCheckScheduler], the HTTP monitors have their own, independently
 * scheduled SSL checks as well, which are hooked into the lifecycle of the uptime checks.
 */
@Singleton
class HttpCheckScheduler(
    @Named(TaskExecutors.SCHEDULED) taskScheduler: TaskScheduler,
    monitorRepository: HttpMonitorRepository,
    private val uptimeChecker: HttpUptimeChecker,
    private val sslChecker: SSLChecker,
    dispatcher: CoroutineDispatcher,
    lockRegistry: UptimeCheckLockRegistry,
    maintenanceWindowService: MaintenanceWindowService,
) : UptimeCheckScheduler<HttpMonitorRecord>(
    taskScheduler,
    monitorRepository,
    dispatcher,
    lockRegistry,
    maintenanceWindowService,
) {
    private val scheduledSSLChecks: ConcurrentHashMap<Long, ScheduledFuture<*>> = ConcurrentHashMap()

    override val monitorType = MonitorType.HTTP_SSL

    override val HttpMonitorRecord.checkTarget: String
        get() = safeDisplayUrl

    override suspend fun runCheck(monitor: HttpMonitorRecord, doAfter: (HttpMonitorRecord) -> Unit) =
        uptimeChecker.check(monitor, doAfter = doAfter)

    override fun scheduleAdditionalChecks(monitor: HttpMonitorRecord) {
        if (monitor.sslCheckEnabled) {
            scheduleSSLCheck(monitor)
        } else {
            // The SSL check might have been turned off by an update, so the previous one has to be cancelled
            cancelAdditionalChecks(monitor)
        }
    }

    override fun cancelAdditionalChecks(monitor: HttpMonitorRecord) {
        scheduledSSLChecks[monitor.id].gracefulCancel()
        scheduledSSLChecks.remove(monitor.id)
    }

    override fun cancelAllAdditionalChecks() {
        scheduledSSLChecks.forEach { it.value.gracefulCancel() }
        scheduledSSLChecks.clear()
    }

    fun getScheduledSSLChecks() = scheduledSSLChecks.toMap()

    /**
     * Calculates the time of the next SSL check of a given monitor.
     */
    fun getNextSSLCheck(monitorId: Long): OffsetDateTime? = scheduledSSLChecks[monitorId]?.getNextCheck()

    /**
     * Takes care of the actual scheduling of the SSL check. When [initialDelay] is not provided, the first check is
     * spread out randomly to prevent flooding right after startup.
     *
     * A failure here is only logged, never propagated: the SSL check is an addition to the uptime check, so it must
     * not fail the whole (re)scheduling of the monitor.
     */
    private fun scheduleSSLCheck(
        monitor: HttpMonitorRecord,
        initialDelay: Duration = Duration.ofSeconds(
            (SSL_CHECK_INITIAL_DELAY_MIN_SECONDS..SSL_CHECK_INITIAL_DELAY_MAX_SECONDS).random()
        ),
    ) {
        val period = Duration.ofDays(SSL_CHECK_PERIOD_DAYS)
        runCatching {
            taskScheduler.scheduleWithFixedDelay(initialDelay, period) {
                runSSLCheck(monitor)
            }
        }.fold(
            onSuccess = { scheduledTask ->
                scheduledSSLChecks[monitor.id].gracefulCancel()
                scheduledSSLChecks[monitor.id] = scheduledTask
                logger.debug(
                    "SSL check for ${monitor.label} has been set up successfully. " +
                        "Next check will happen around: ${scheduledTask.getNextCheck()}"
                )
            },
            onFailure = { error ->
                logger.error("SSL check for ${monitor.label} cannot be set up: ${error.message}")
            },
        )
    }

    /**
     * The periodic SSL check task, running directly on the task scheduler's thread pool. An
     * escaping exception would make the executor cancel the repeating task silently, stopping the SSL checks of the
     * monitor until the next restart, hence the catch-all here.
     */
    @Suppress("TooGenericExceptionCaught")
    internal fun runSSLCheck(monitor: HttpMonitorRecord) {
        try {
            if (maintenanceWindowService.isUnderMaintenance(monitor.monitorId())) {
                // SSL checks only run once a day, so simply skipping them under maintenance could delay a check until
                // the next day (or indefinitely for daily recurring maintenance). Instead, we re-schedule the check
                // with a short initial delay, effectively retrying until the maintenance window is over.
                logger.debug(
                    "Postponing SSL check for \"${monitor.name}\" by $SSL_CHECK_POSTPONE_MINUTES minutes: " +
                        "it is under maintenance"
                )
                scheduleSSLCheck(monitor, initialDelay = Duration.ofMinutes(SSL_CHECK_POSTPONE_MINUTES))
            } else {
                sslChecker.check(monitor)
            }
        } catch (ex: Exception) {
            logger.error(
                "An unexpected error happened during the SSL check of a monitor (${monitor.name}): ${ex.message}",
                ex,
            )
        }
    }

    companion object {
        private const val SSL_CHECK_INITIAL_DELAY_MIN_SECONDS = 60L
        private const val SSL_CHECK_INITIAL_DELAY_MAX_SECONDS = 300L
        private const val SSL_CHECK_PERIOD_DAYS = 1L
        private const val SSL_CHECK_POSTPONE_MINUTES = 30L
    }
}
