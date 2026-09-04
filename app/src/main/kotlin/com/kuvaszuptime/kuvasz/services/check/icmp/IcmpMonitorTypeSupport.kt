package com.kuvaszuptime.kuvasz.services.check.icmp

import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.services.monitor.MetricsHistoryMonitorTypeSupport
import jakarta.inject.Singleton

@Singleton
class IcmpMonitorTypeSupport(
    override val repository: IcmpMonitorRepository,
    override val metricsLogRepository: IcmpMetricsLogRepository,
) : MetricsHistoryMonitorTypeSupport<IcmpMonitorCreator, IcmpMonitorRecord, IcmpMonitorDetailsDto>()
