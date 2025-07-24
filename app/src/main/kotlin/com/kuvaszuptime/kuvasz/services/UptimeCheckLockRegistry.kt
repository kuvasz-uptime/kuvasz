package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Singleton
class UptimeCheckLockRegistry(private val appConfig: AppConfig) {
    private val activeChecks = ConcurrentHashMap<Long, Instant>()

    fun tryAcquire(monitorId: Long): Boolean {
        val isLockActive = activeChecks[monitorId]?.isAfter(Instant.now()) ?: false

        return if (isLockActive) {
            logger.debug("Uptime check for monitor with ID: $monitorId is already running, failed to acquire lock")
            false
        } else {
            activeChecks[monitorId] = Instant.now().plusMillis(appConfig.uptimeCheckLockTimeoutMs)
            logger.debug("Uptime check for monitor with ID: $monitorId is not running, acquired lock")
            true
        }
    }

    fun release(monitorId: Long) {
        activeChecks.remove(monitorId)
        logger.debug("Uptime check for monitor with ID: $monitorId is completed, released lock")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UptimeCheckLockRegistry::class.java)
    }
}
