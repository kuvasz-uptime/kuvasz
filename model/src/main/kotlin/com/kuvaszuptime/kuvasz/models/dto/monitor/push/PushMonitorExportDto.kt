package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected

@Introspected
data class PushMonitorExportDto(
    val name: String,
    val heartbeatInterval: Long,
    val gracePeriod: Long,
    val clientSecret: String,
    val enabled: Boolean,
    val integrations: Set<IntegrationID>,
) {
    companion object {
        fun fromMonitorRecord(record: PushMonitorRecord): PushMonitorExportDto {
            return PushMonitorExportDto(
                name = record.name,
                heartbeatInterval = record.heartbeatInterval,
                gracePeriod = record.gracePeriod,
                clientSecret = record.clientSecret,
                enabled = record.enabled,
                integrations = record.integrations.toSet(),
            )
        }
    }
}
