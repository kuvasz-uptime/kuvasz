package com.kuvaszuptime.kuvasz.models.dto.monitor.push.heartbeat

import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDocs
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class PushMonitorFailureDetailsDto(
    @param:Schema(description = PushMonitorDocs.EXPLICIT_FAILURE_MESSAGE, required = false, nullable = false)
    val error: String?
)
