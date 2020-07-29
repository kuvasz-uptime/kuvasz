package com.akobor.kuvasz.services

import com.akobor.kuvasz.repositories.MonitorRepository
import com.akobor.kuvasz.tables.pojos.MonitorPojo
import com.akobor.kuvasz.util.toDurationOfSeconds
import io.micronaut.context.annotation.Context
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import org.slf4j.LoggerFactory
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Named

@Context
class CheckScheduler @Inject constructor(
    private val monitorRepository: MonitorRepository,
    private val uptimeChecker: UptimeChecker
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CheckScheduler::class.java)
    }

    @Inject
    @Named(TaskExecutors.SCHEDULED)
    lateinit var taskScheduler: TaskScheduler

    private val scheduledChecks: MutableMap<Int, ScheduledFuture<*>> = mutableMapOf()

    @PostConstruct
    fun initialize() {
        monitorRepository.fetchByEnabled(true)
            .forEach { monitor ->
                val scheduledUptimeCheck = scheduleUptimeCheck(monitor)
                if (scheduledUptimeCheck != null) {
                    scheduledChecks[monitor.id] = scheduledUptimeCheck
                }
            }
    }

    private fun scheduleUptimeCheck(monitorPojo: MonitorPojo): ScheduledFuture<*>? {
        return try {
            val period = monitorPojo.uptimeCheckInterval.toDurationOfSeconds()
            val task = taskScheduler.scheduleAtFixedRate(period, period) {
                uptimeChecker.check(monitorPojo)
            }
            logger.info("Uptime check for \"${monitorPojo.name}\" (${monitorPojo.url}) has been set up successfully")
            task
        } catch (e: RejectedExecutionException) {
            logger.error("Uptime check for \"${monitorPojo.name}\" (${monitorPojo.url}) cannot be set up: ${e.message}")
            null
        }
    }
}
