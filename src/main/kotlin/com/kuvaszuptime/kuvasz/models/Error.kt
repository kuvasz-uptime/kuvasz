package com.kuvaszuptime.kuvasz.models

import io.micronaut.core.annotation.Introspected

@Introspected
data class ServiceError(
    val message: String? = "Something bad happened :("
)

class MonitorNotFoundError(
    private val monitorId: Int,
    override val message: String? = "There is no monitor with id: $monitorId"
) : Throwable()
