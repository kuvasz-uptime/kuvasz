package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class StatusPageDetailsDto(
    @param:Schema(description = StatusPageDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = StatusPageDocs.TITLE, required = true)
    val title: String,
    @param:Schema(description = StatusPageDocs.SLUG, required = true)
    val slug: String?,
    @param:Schema(description = StatusPageDocs.CUSTOM_LOGO_URL, required = true)
    val customLogoUrl: String?,
    @param:Schema(description = StatusPageDocs.CUSTOM_FAVICON_URL, required = true)
    val customFaviconUrl: String?,
    @param:Schema(description = StatusPageDocs.PUBLIC, required = true)
    val public: Boolean,
    @param:Schema(description = StatusPageDocs.SYSTEM_STATUS, required = true)
    val systemStatus: SystemStatus,
    @param:Schema(description = StatusPageDocs.STATUS_GENERATED_AT, required = true)
    val generatedAt: OffsetDateTime,
    @param:Schema(description = StatusPageDocs.MONITOR_DETAILS, required = true)
    val monitors: List<StatusPageMonitorDetailsDto>,
    @param:Schema(description = StatusPageDocs.ACTIVE_MAINTENANCE_WINDOWS, required = true)
    val activeMaintenanceWindows: List<StatusPageMaintenanceWindowDto> = emptyList(),
    @param:Schema(description = StatusPageDocs.UPCOMING_MAINTENANCE_WINDOWS, required = true)
    val upcomingMaintenanceWindows: List<StatusPageMaintenanceWindowDto> = emptyList(),
)
