package com.kuvaszuptime.kuvasz.models.dto

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotBlank

@Introspected
data class PagerdutyKeyUpdateDto(
    @get:NotBlank
    val pagerdutyIntegrationKey: String
)
