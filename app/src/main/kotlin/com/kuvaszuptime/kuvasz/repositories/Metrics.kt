package com.kuvaszuptime.kuvasz.repositories

import io.micronaut.core.annotation.Introspected

const val P90 = .90
const val P95 = .95
const val P99 = .99

@Introspected
data class LatencyMetricResult(
    val monitorId: Long,
    val avg: Int?,
    val min: Int?,
    val max: Int?,
    val p90: Int?,
    val p95: Int?,
    val p99: Int?,
)
