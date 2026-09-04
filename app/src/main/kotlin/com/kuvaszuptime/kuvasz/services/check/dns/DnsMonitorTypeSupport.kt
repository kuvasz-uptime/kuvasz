package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMonitorCreator
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.services.monitor.MetricsHistoryMonitorTypeSupport
import jakarta.inject.Singleton

@Singleton
class DnsMonitorTypeSupport(
    override val repository: DnsMonitorRepository,
    override val metricsLogRepository: DnsMetricsLogRepository,
) : MetricsHistoryMonitorTypeSupport<DnsMonitorCreator, DnsMonitorRecord, DnsMonitorDetailsDto>()
