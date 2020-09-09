package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import io.micronaut.core.annotation.Introspected
import java.net.URI
import java.time.OffsetDateTime

@Introspected
data class MonitorDetailsDto(
    val id: Int,
    val name: String,
    val url: URI,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val sslCheckEnabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?,
    val uptimeStatus: UptimeStatus?,
    val uptimeStatusStartedAt: OffsetDateTime?,
    val lastUptimeCheck: OffsetDateTime?,
    val sslStatus: SslStatus?,
    val sslStatusStartedAt: OffsetDateTime?,
    val lastSSLCheck: OffsetDateTime?,
    val uptimeError: String?,
    val sslError: String?,
    val averageLatencyInMs: Int?,
    val p95LatencyInMs: Int?,
    val p99LatencyInMs: Int?
)
