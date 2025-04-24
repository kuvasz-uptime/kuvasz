package com.kuvaszuptime.kuvasz.services

import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

@Singleton
class UptimeCheckLockRegistry {
    private val mutex = Mutex()
    private val activeMonitors = mutableSetOf<Int>()

    suspend fun tryAcquire(monitorId: Int): Boolean = mutex.withLock {
        if (activeMonitors.contains(monitorId)) {
            logger.debug("Uptime check for monitor with ID: $monitorId is already running, failed to acquire lock")
            false
        } else {
            activeMonitors.add(monitorId)
            logger.debug("Uptime check for monitor with ID: $monitorId is not running, acquired lock")
            true
        }
    }

    suspend fun release(monitorId: Int) = mutex.withLock {
        activeMonitors.remove(monitorId)
        logger.debug("Uptime check for monitor with ID: $monitorId is completed, released lock")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UptimeCheckLockRegistry::class.java)
    }
}
