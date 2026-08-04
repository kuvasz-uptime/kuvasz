package com.kuvaszuptime.kuvasz.services.check.icmp

import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckScheduler
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Singleton
class IcmpCheckScheduler(
    @Named(TaskExecutors.SCHEDULED) taskScheduler: TaskScheduler,
    monitorRepository: IcmpMonitorRepository,
    private val uptimeChecker: IcmpUptimeChecker,
    dispatcher: CoroutineDispatcher,
    lockRegistry: UptimeCheckLockRegistry,
    maintenanceWindowService: MaintenanceWindowService,
) : UptimeCheckScheduler<IcmpMonitorRecord>(
    taskScheduler,
    monitorRepository,
    dispatcher,
    lockRegistry,
    maintenanceWindowService,
) {
    override val monitorType = MonitorType.ICMP

    override val IcmpMonitorRecord.checkTarget: String
        get() = host

    override suspend fun runCheck(monitor: IcmpMonitorRecord, doAfter: (IcmpMonitorRecord) -> Unit) =
        uptimeChecker.check(monitor, doAfter)
}
