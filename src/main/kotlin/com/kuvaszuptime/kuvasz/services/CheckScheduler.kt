package com.kuvaszuptime.kuvasz.services

import arrow.core.Either
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.models.ScheduledCheck
import com.kuvaszuptime.kuvasz.models.SchedulingError
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.util.catchBlocking
import com.kuvaszuptime.kuvasz.util.toDurationOfSeconds
import io.micronaut.context.annotation.Context
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledFuture
import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Named

@Context
class CheckScheduler @Inject constructor(
    @Named(TaskExecutors.SCHEDULED) private val taskScheduler: TaskScheduler,
    private val monitorRepository: MonitorRepository,
    private val uptimeChecker: UptimeChecker
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CheckScheduler::class.java)
    }

    private val scheduledChecks: MutableList<ScheduledCheck> = mutableListOf()

    @PostConstruct
    fun initialize() {
        monitorRepository.fetchByEnabled(true)
            .forEach { createChecksForMonitor(it) }
    }

    fun getScheduledChecks() = scheduledChecks

    fun createChecksForMonitor(monitor: MonitorPojo): Either<SchedulingError, ScheduledCheck> =
        scheduleUptimeCheck(monitor).bimap(
            { e ->
                logger.error(
                    "Uptime check for \"${monitor.name}\" (${monitor.url}) cannot be set up: ${e.message}"
                )
                SchedulingError(e.message)
            },
            { scheduledTask ->
                val scheduledCheck =
                    ScheduledCheck(checkType = CheckType.UPTIME, monitorId = monitor.id, task = scheduledTask)
                scheduledChecks.add(scheduledCheck)
                logger.info("Uptime check for \"${monitor.name}\" (${monitor.url}) has been set up successfully")

                scheduledCheck
            }
        )

    fun removeChecksOfMonitor(monitor: MonitorPojo) {
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
        existingMonitor: MonitorPojo,
        updatedMonitor: MonitorPojo
    ): Either<SchedulingError, ScheduledCheck> {
        removeChecksOfMonitor(existingMonitor)
        return createChecksForMonitor(updatedMonitor)
    }

    private fun scheduleUptimeCheck(monitor: MonitorPojo): Either<Throwable, ScheduledFuture<*>> =
        Either.catchBlocking {
            val period = monitor.uptimeCheckInterval.toDurationOfSeconds()
            taskScheduler.scheduleAtFixedRate(period, period) {
                uptimeChecker.check(monitor)
            }
        }
}
