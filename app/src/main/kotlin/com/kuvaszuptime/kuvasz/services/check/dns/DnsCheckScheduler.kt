package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckScheduler
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Singleton
class DnsCheckScheduler(
    @Named(TaskExecutors.SCHEDULED) taskScheduler: TaskScheduler,
    monitorRepository: DnsMonitorRepository,
    private val uptimeChecker: DnsUptimeChecker,
    dispatcher: CoroutineDispatcher,
    lockRegistry: UptimeCheckLockRegistry,
    maintenanceWindowService: MaintenanceWindowService,
) : UptimeCheckScheduler<DnsMonitorRecord>(
    taskScheduler,
    monitorRepository,
    dispatcher,
    lockRegistry,
    maintenanceWindowService,
) {
    override val monitorType = MonitorType.DNS

    override val DnsMonitorRecord.checkTarget: String
        get() = host

    override suspend fun runCheck(monitor: DnsMonitorRecord, doAfter: (DnsMonitorRecord) -> Unit) =
        uptimeChecker.check(monitor, doAfter)
}
