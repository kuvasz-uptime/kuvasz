package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class SSLEventDto(
    @Schema(description = "Unique identifier for the SSL event", required = true)
    val id: Long,
    @Schema(description = "The status of the SSL certificate", required = true)
    val status: SslStatus,
    @Schema(description = "The error that occurred during the SSL check, if any", required = true, nullable = true)
    val error: String?,
    @Schema(description = "The timestamp when the SSL event started", required = true)
    val startedAt: OffsetDateTime,
    @Schema(description = "The timestamp the SSL certificate is valid until", required = true, nullable = true)
    val sslValidUntil: OffsetDateTime?,
    @Schema(description = "The timestamp when the SSL event ended, if applicable", required = true, nullable = true)
    val endedAt: OffsetDateTime?,
    @Schema(description = "The timestamp when the SSL event was updated", required = true)
    val updatedAt: OffsetDateTime
)
