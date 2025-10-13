package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class PushMonitorStatsDto(
    @param:Schema(description = PushMonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = "Uptime related statistics of the monitor in the given period", required = true)
    val uptimeHistory: HistoricalUptimeStatsDto,
)
