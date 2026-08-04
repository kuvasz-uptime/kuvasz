package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckScheduler
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Singleton
class TcpCheckScheduler(
    @Named(TaskExecutors.SCHEDULED) taskScheduler: TaskScheduler,
    monitorRepository: TcpMonitorRepository,
    private val uptimeChecker: TcpUptimeChecker,
    dispatcher: CoroutineDispatcher,
    lockRegistry: UptimeCheckLockRegistry,
    maintenanceWindowService: MaintenanceWindowService,
) : UptimeCheckScheduler<TcpMonitorRecord>(
    taskScheduler,
    monitorRepository,
    dispatcher,
    lockRegistry,
    maintenanceWindowService,
) {
    override val monitorType = MonitorType.TCP

    override val TcpMonitorRecord.checkTarget: String
        get() = "$host:$port"

    override suspend fun runCheck(monitor: TcpMonitorRecord, doAfter: (TcpMonitorRecord) -> Unit) =
        uptimeChecker.check(monitor, doAfter)
}
