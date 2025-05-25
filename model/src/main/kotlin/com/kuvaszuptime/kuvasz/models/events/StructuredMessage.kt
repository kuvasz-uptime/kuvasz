package com.kuvaszuptime.kuvasz.models.events

sealed class StructuredMessage {
    abstract val summary: String
}

sealed class StructuredMonitorMessage : StructuredMessage()

data class StructuredMonitorUpMessage(
    override val summary: String,
    val latency: String,
    val previousDownTime: String?
) : StructuredMonitorMessage()

data class StructuredMonitorDownMessage(
    override val summary: String,
    val error: String,
    val previousUpTime: String?
) : StructuredMonitorMessage()

data class StructuredRedirectMessage(
    override val summary: String
) : StructuredMessage()

sealed class StructuredSSLMessage : StructuredMessage()

data class StructuredSSLValidMessage(
    override val summary: String,
    val previousInvalidEvent: String?
) : StructuredSSLMessage()

data class StructuredSSLInvalidMessage(
    override val summary: String,
    val error: String,
    val previousValidEvent: String?
) : StructuredSSLMessage()

data class StructuredSSLWillExpireMessage(
    override val summary: String,
    val validUntil: String
) : StructuredSSLMessage()
