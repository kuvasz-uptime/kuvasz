package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import io.micronaut.core.annotation.Introspected
import java.time.OffsetDateTime

@Introspected
data class MonitorDto(
    val id: Int,
    val name: String,
    val url: String,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val sslCheckEnabled: Boolean,
    val pagerdutyKeyPresent: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?
) {
    companion object {
        fun fromMonitorPojo(pojo: MonitorPojo) =
            MonitorDto(
                id = pojo.id,
                name = pojo.name,
                url = pojo.url,
                uptimeCheckInterval = pojo.uptimeCheckInterval,
                enabled = pojo.enabled,
                sslCheckEnabled = pojo.sslCheckEnabled,
                pagerdutyKeyPresent = !pojo.pagerdutyIntegrationKey.isNullOrBlank(),
                createdAt = pojo.createdAt,
                updatedAt = pojo.updatedAt
            )
    }
}
