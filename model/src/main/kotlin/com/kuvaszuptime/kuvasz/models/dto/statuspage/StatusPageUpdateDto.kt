package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.models.dto.StatusPageValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

@Introspected
data class StatusPageUpdateDto(
    @param:Schema(description = StatusPageDocs.TITLE, required = false, nullable = false)
    @get:NotBlank(message = StatusPageValidationMessages.TITLE_NOT_BLANK)
    val title: String?,

    @param:Schema(description = StatusPageDocs.SLUG, required = false, nullable = false)
    @get:NotBlank(message = StatusPageValidationMessages.SLUG_NOT_BLANK)
    @get:Pattern(regexp = Validation.SLUG_REGEX, message = StatusPageValidationMessages.SLUG_PATTERN)
    val slug: String?,

    @param:Schema(description = StatusPageDocs.CUSTOM_LOGO_URL, required = false, nullable = true)
    val customLogoUrl: String?,

    @param:Schema(description = StatusPageDocs.CUSTOM_FAVICON_URL, required = false, nullable = true)
    val customFaviconUrl: String?,

    @get:NotNull
    @param:Schema(description = StatusPageDocs.PUBLIC, required = false, nullable = false)
    val public: Boolean?,

    @param:Schema(description = StatusPageDocs.MONITORS, required = false, nullable = true)
    val monitors: Set<MonitorID>?,
)
