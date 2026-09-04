package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.monitor.MetricsHistoryMonitorTypeSupport
import jakarta.inject.Singleton

@Singleton
class HttpMonitorTypeSupport(
    override val repository: HttpMonitorRepository,
    override val metricsLogRepository: HttpLatencyLogRepository,
) : MetricsHistoryMonitorTypeSupport<HttpMonitorCreator, HttpMonitorRecord, HttpMonitorDetailsDto>()
