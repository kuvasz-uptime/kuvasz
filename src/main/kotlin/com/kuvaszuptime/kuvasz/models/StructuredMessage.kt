package com.kuvaszuptime.kuvasz.models

import arrow.core.Option

data class StructuredMonitorUpMessage(
    val summary: String,
    val latency: String,
    val previousDownTime: Option<String>
)

data class StructuredMonitorDownMessage(
    val summary: String,
    val error: String,
    val previousUpTime: Option<String>
)

data class StructuredSSLValidMessage(
    val summary: String,
    val previousInvalidEvent: Option<String>
)

data class StructuredSSLInvalidMessage(
    val summary: String,
    val error: String,
    val previousValidEvent: Option<String>
)

data class StructuredSSLWillExpireMessage(
    val summary: String,
    val validUntil: String
)
