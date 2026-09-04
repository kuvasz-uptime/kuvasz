package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.tcp.TcpMonitorCreator
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.services.monitor.MetricsHistoryMonitorTypeSupport
import jakarta.inject.Singleton

@Singleton
class TcpMonitorTypeSupport(
    override val repository: TcpMonitorRepository,
    override val metricsLogRepository: TcpMetricsLogRepository,
) : MetricsHistoryMonitorTypeSupport<TcpMonitorCreator, TcpMonitorRecord, TcpMonitorDetailsDto>()
