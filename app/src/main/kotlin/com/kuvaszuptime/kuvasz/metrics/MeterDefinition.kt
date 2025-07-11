package com.kuvaszuptime.kuvasz.metrics

import io.micrometer.core.instrument.Meter

data class MeterDefinition<T : Any>(
    val id: Meter.Id,
    val value: T,
)
