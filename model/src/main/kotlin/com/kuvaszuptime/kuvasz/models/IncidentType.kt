package com.kuvaszuptime.kuvasz.models

import io.micronaut.core.annotation.Introspected

@Introspected
enum class IncidentType {
    HTTP,
    SSL,
    PUSH,
}
