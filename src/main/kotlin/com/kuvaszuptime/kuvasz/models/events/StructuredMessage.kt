package com.kuvaszuptime.kuvasz.models.events

import arrow.core.Option

sealed class StructuredMessage

sealed class StructuredMonitorMessage : StructuredMessage()

data class StructuredMonitorUpMessage(
    val summary: String,
    val latency: String,
    val previousDownTime: Option<String>
) : StructuredMonitorMessage()

data class StructuredMonitorDownMessage(
    val summary: String,
    val error: String,
    val previousUpTime: Option<String>
) : StructuredMonitorMessage()

data class StructuredRedirectMessage(
    val summary: String
) : StructuredMessage()

sealed class StructuredSSLMessage : StructuredMessage()

data class StructuredSSLValidMessage(
    val summary: String,
    val previousInvalidEvent: Option<String>
) : StructuredSSLMessage()

data class StructuredSSLInvalidMessage(
    val summary: String,
    val error: String,
    val previousValidEvent: Option<String>
) : StructuredSSLMessage()

data class StructuredSSLWillExpireMessage(
    val summary: String,
    val validUntil: String
) : StructuredSSLMessage()
