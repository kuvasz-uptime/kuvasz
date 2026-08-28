package com.kuvaszuptime.kuvasz.models.events

sealed class StructuredMessage {
    abstract val summary: String
}

sealed class StructuredMonitorMessage : StructuredMessage()

data class StructuredPushMonitorUpMessage(
    override val summary: String,
    val previousDownTime: String?,
) : StructuredMonitorMessage()

data class StructuredIcmpMonitorUpMessage(
    override val summary: String,
    val latency: String?,
    val packetLoss: String,
    val previousDownTime: String?,
) : StructuredMonitorMessage()

data class StructuredIcmpMonitorDownMessage(
    override val summary: String,
    val error: String,
    val packetLoss: String,
    val previousUpTime: String?,
) : StructuredMonitorMessage()

data class StructuredTcpMonitorUpMessage(
    override val summary: String,
    val latency: String?,
    val previousDownTime: String?,
) : StructuredMonitorMessage()

data class StructuredTcpMonitorDownMessage(
    override val summary: String,
    val error: String,
    val previousUpTime: String?,
) : StructuredMonitorMessage()

data class StructuredDnsMonitorUpMessage(
    override val summary: String,
    val latency: String?,
    val previousDownTime: String?,
) : StructuredMonitorMessage()

data class StructuredDnsMonitorDownMessage(
    override val summary: String,
    val error: String,
    val previousUpTime: String?,
) : StructuredMonitorMessage()

data class StructuredHttpMonitorUpMessage(
    override val summary: String,
    val latency: String,
    val previousDownTime: String?,
) : StructuredMonitorMessage()

data class StructuredMonitorDownMessage(
    override val summary: String,
    val error: String,
    val previousUpTime: String?,
) : StructuredMonitorMessage()

data class StructuredRedirectMessage(
    override val summary: String,
) : StructuredMessage()

data class StructuredDnsRecordsChangedMessage(
    override val summary: String,
    val details: String,
) : StructuredMessage()

sealed class StructuredSSLMessage : StructuredMessage()

data class StructuredSSLValidMessage(
    override val summary: String,
    val previousInvalidEvent: String?,
) : StructuredSSLMessage()

data class StructuredSSLInvalidMessage(
    override val summary: String,
    val error: String,
    val previousValidEvent: String?,
) : StructuredSSLMessage()

data class StructuredSSLWillExpireMessage(
    override val summary: String,
    val validUntil: String,
) : StructuredSSLMessage()

sealed class StructuredMaintenanceMessage : StructuredMessage()

data class StructuredMaintenanceStartMessage(
    override val summary: String,
    val description: String?,
) : StructuredMaintenanceMessage()

data class StructuredMaintenanceEndMessage(
    override val summary: String,
) : StructuredMaintenanceMessage()
