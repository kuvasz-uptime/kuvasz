package com.kuvaszuptime.kuvasz.models

import arrow.core.Option

data class StructuredUpMessage(
    val summary: String,
    val latency: String,
    val previousDownTime: Option<String>
)

data class StructuredDownMessage(
    val summary: String,
    val error: String,
    val previousUpTime: Option<String>
)
