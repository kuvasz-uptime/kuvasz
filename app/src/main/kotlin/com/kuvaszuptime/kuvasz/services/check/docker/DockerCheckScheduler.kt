package com.kuvaszuptime.kuvasz.services.check.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckScheduler
import com.kuvaszuptime.kuvasz.services.connectivity.ConnectivityChecker
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Singleton
class DockerCheckScheduler(
    @Named(TaskExecutors.SCHEDULED) taskScheduler: TaskScheduler,
    monitorRepository: DockerMonitorRepository,
    private val uptimeChecker: DockerUptimeChecker,
    dispatcher: CoroutineDispatcher,
    lockRegistry: UptimeCheckLockRegistry,
    maintenanceWindowService: MaintenanceWindowService,
    connectivityChecker: ConnectivityChecker?,
) : UptimeCheckScheduler<DockerMonitorRecord>(
    taskScheduler,
    monitorRepository,
    dispatcher,
    lockRegistry,
    maintenanceWindowService,
    connectivityChecker,
) {
    override val monitorType = MonitorType.DOCKER

    override val DockerMonitorRecord.checkTarget: String
        get() = "$dockerHost/$container"

    override suspend fun runCheck(monitor: DockerMonitorRecord, doAfter: (DockerMonitorRecord) -> Unit) =
        uptimeChecker.check(monitor, doAfter)
}
