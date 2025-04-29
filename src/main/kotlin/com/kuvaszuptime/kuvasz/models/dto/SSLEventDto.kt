package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.SslStatus
import io.micronaut.core.annotation.Introspected
import java.time.OffsetDateTime

@Introspected
data class SSLEventDto(
    val id: Long,
    val status: SslStatus,
    val error: String?,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime
)
