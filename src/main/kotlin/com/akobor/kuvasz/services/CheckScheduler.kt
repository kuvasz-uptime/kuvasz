package com.akobor.kuvasz.services

import arrow.core.Either
import com.akobor.kuvasz.repositories.MonitorRepository
import com.akobor.kuvasz.tables.pojos.MonitorPojo
import com.akobor.kuvasz.util.catchBlocking
import com.akobor.kuvasz.util.toDurationOfSeconds
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
                scheduleUptimeCheck(monitor).fold(
                    { e ->
                        logger.error(
                            "Uptime check for \"${monitor.name}\" (${monitor.url}) cannot be set up: ${e.message}"
                        )
                    },
                    { scheduledUptimeCheck ->
                        scheduledChecks[monitor.id] = scheduledUptimeCheck
                        logger.info(
                            "Uptime check for \"${monitor.name}\" (${monitor.url}) has been set up successfully"
                        )
                    }
                )
            }
    }

    private fun scheduleUptimeCheck(monitor: MonitorPojo): Either<Throwable, ScheduledFuture<*>> =
        Either.catchBlocking {
            val period = monitor.uptimeCheckInterval.toDurationOfSeconds()
            taskScheduler.scheduleAtFixedRate(period, period) {
                uptimeChecker.check(monitor)
            }
        }
}
