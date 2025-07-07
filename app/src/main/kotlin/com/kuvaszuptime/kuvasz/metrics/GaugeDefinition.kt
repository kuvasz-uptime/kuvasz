package com.kuvaszuptime.kuvasz.metrics

import io.micrometer.core.instrument.Meter
import java.util.concurrent.atomic.AtomicLong

data class GaugeDefinition(
    val id: Meter.Id,
    val value: AtomicLong,
)
