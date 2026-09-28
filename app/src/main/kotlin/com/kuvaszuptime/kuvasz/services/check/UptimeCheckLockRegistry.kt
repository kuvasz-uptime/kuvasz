package com.kuvaszuptime.kuvasz.services.check

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.util.loggerFor
import jakarta.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Singleton
class UptimeCheckLockRegistry(private val appConfig: AppConfig) {
    private val activeChecks = ConcurrentHashMap<Long, Instant>()

    // Guards the start of the draining against the concurrent lock acquisitions
    private val drainLock = Any()

    private var drainDeadline: Instant? = null

    fun tryAcquire(monitorId: Long): Boolean = synchronized(drainLock) {
        val isLockActive = activeChecks[monitorId]?.isAfter(Instant.now()) ?: false

        when {
            drainDeadline != null -> {
                logger.debug("Uptime check for monitor with ID: $monitorId is skipped, the checks are being drained")
                false
            }

            isLockActive -> {
                logger.debug("Uptime check for monitor with ID: $monitorId is already running, failed to acquire lock")
                false
            }

            else -> {
                activeChecks[monitorId] = Instant.now().plusMillis(appConfig.httpCheckLockTimeoutMillis)
                logger.debug("Uptime check for monitor with ID: $monitorId is not running, acquired lock")
                true
            }
        }
    }

    fun release(monitorId: Long) {
        activeChecks.remove(monitorId)
        logger.debug("Uptime check for monitor with ID: $monitorId is completed, released lock")
    }

    /**
     * Prevents any new check from starting (regardless of the monitor type), and waits for the running ones to
     * finish, but at most until the grace period is over. The grace period starts with the first call, so the
     * subsequent calls (i.e. the other schedulers shutting down) only wait for what is left of it.
     */
    fun drain(gracePeriod: Duration = SHUTDOWN_GRACE_PERIOD) {
        val (deadline, isFirstCall) = synchronized(drainLock) {
            drainDeadline?.let { it to false } ?: (Instant.now().plus(gracePeriod).also { drainDeadline = it } to true)
        }
        while (activeChecks.isNotEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(DRAIN_POLL_INTERVAL)
        }
        if (isFirstCall && activeChecks.isNotEmpty()) {
            logger.warn("${activeChecks.size} uptime check(s) did not finish within the shutdown grace period")
        }
    }

    companion object {
        private val logger = loggerFor<UptimeCheckLockRegistry>()
        private val SHUTDOWN_GRACE_PERIOD: Duration = Duration.ofSeconds(5)
        private val DRAIN_POLL_INTERVAL: Duration = Duration.ofMillis(100)
    }
}
