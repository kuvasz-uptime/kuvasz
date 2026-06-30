package com.kuvaszuptime.kuvasz.services.check.push

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.isDownNow
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

@Singleton
class HeartbeatChecker(
    private val dslCtx: DSLContext,
    private val eventDispatcher: EventDispatcher,
    private val pushMonitorRepository: PushMonitorRepository,
    private val uptimeEventRepository: PushUptimeEventRepository,
    private val databaseEventHandler: DatabaseEventHandler,
    private val pendingFailureRepository: PendingFailureRepository,
    private val maintenanceWindowService: MaintenanceWindowService,
) {
    /**
     * Checks every enabled push monitors to see if their expected heartbeats are on time,
     * and emits an UptimeEvent based on the evaluation's result.
     */
    fun checkHeartbeats() {
        dslCtx.transactionResult { config ->
            val txCtx = config.dsl()
            pushMonitorRepository.fetchWithMissedHeartbeats(txCtx).forEach { monitor ->
                // Skip monitors that are under maintenance
                if (maintenanceWindowService.isUnderMaintenance(monitor.monitorId())) return@forEach
                PushMonitorDownEvent(
                    monitor,
                    error = Messages.missedHeartbeat(),
                    previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitor.id, txCtx),
                ).also { event ->
                    if (event.isDownNow(pendingFailureRepository)) {
                        databaseEventHandler.handleUptimeMonitorEvent(event)
                        eventDispatcher.dispatch(event)
                    }
                }
            }
        }
    }
}

/**
 * The scheduled job for checking that push monitors' heartbeats are on time
 */
@Singleton
@Requires(notEnv = [Environment.TEST])
class HeartbeatCheckScheduler(
    private val heartbeatChecker: HeartbeatChecker,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * The minimum heartbeat interval is 10 seconds currently, so the initial delay is set to
     * a slightly higher value to make push monitors able to send a heartbeat after restart.
     */
    @Scheduled(fixedDelay = "5s", initialDelay = "12s")
    fun check() {
        logger.debug("Starting heartbeat checks...")
        heartbeatChecker.checkHeartbeats()
        logger.debug("Heartbeat checks has been completed")
    }
}
