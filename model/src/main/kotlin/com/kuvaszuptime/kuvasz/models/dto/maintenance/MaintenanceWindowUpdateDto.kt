package com.kuvaszuptime.kuvasz.models.dto.maintenance

import com.kuvaszuptime.kuvasz.models.dto.MaintenanceWindowValidationMessages
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceSchedule
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.validation.ValidCron
import com.kuvaszuptime.kuvasz.validation.ValidDuration
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

@Introspected
data class MaintenanceWindowUpdateDto(
    @param:Schema(description = MaintenanceWindowDocs.NAME, required = false, nullable = false)
    @get:NotBlank(message = MaintenanceWindowValidationMessages.NAME_NOT_BLANK)
    val name: String?,

    @param:Schema(description = MaintenanceWindowDocs.DESCRIPTION, required = false, nullable = true)
    val description: String?,

    @get:NotNull
    @param:Schema(description = MaintenanceWindowDocs.ENABLED, required = false, nullable = false)
    val enabled: Boolean?,

    @get:NotNull
    @param:Schema(description = MaintenanceWindowDocs.GLOBAL, required = false, nullable = false)
    val global: Boolean?,

    @get:NotNull
    @param:Schema(description = MaintenanceWindowDocs.SHOW_ON_STATUS_PAGES, required = false, nullable = false)
    val showOnStatusPages: Boolean?,

    @get:ValidCron
    @param:Schema(description = MaintenanceWindowDocs.CRON, required = false, nullable = true)
    override val cron: String?,

    @param:Schema(description = MaintenanceWindowDocs.START, required = false, nullable = true)
    override val start: OffsetDateTime?,

    @get:ValidDuration
    @param:Schema(description = MaintenanceWindowDocs.DURATION, required = false, nullable = true)
    override val duration: String?,

    @param:Schema(description = MaintenanceWindowDocs.MONITORS, required = false, nullable = true)
    val monitors: Set<MonitorID>?,

    @param:Schema(description = MaintenanceWindowDocs.INTEGRATIONS, required = false, nullable = true)
    val integrations: Set<IntegrationID>?,
) : MaintenanceSchedule
