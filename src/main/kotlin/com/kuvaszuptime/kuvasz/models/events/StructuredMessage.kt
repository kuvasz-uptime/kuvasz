package com.kuvaszuptime.kuvasz.models.events

sealed class StructuredMessage

sealed class StructuredMonitorMessage : StructuredMessage()

data class StructuredMonitorUpMessage(
    val summary: String,
    val latency: String,
    val previousDownTime: String?
) : StructuredMonitorMessage()

data class StructuredMonitorDownMessage(
    val summary: String,
    val error: String,
    val previousUpTime: String?
) : StructuredMonitorMessage()

data class StructuredRedirectMessage(
    val summary: String
) : StructuredMessage()

sealed class StructuredSSLMessage : StructuredMessage()

data class StructuredSSLValidMessage(
    val summary: String,
    val previousInvalidEvent: String?
) : StructuredSSLMessage()

data class StructuredSSLInvalidMessage(
    val summary: String,
    val error: String,
    val previousValidEvent: String?
) : StructuredSSLMessage()

data class StructuredSSLWillExpireMessage(
    val summary: String,
    val validUntil: String
) : StructuredSSLMessage()
