package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.CertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.tables.pojos.SslEventPojo
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.micronaut.http.HttpStatus
import java.net.URI
import kotlin.time.Duration

sealed class MonitorEvent {
    abstract val monitor: MonitorPojo

    abstract fun toStructuredMessage(): StructuredMessage

    val dispatchedAt = getCurrentTimestamp()
}

sealed class UptimeMonitorEvent : MonitorEvent() {
    abstract val previousEvent: UptimeEventPojo?

    abstract val uptimeStatus: UptimeStatus

    fun statusNotEquals(previousEvent: UptimeEventPojo) = !statusEquals(previousEvent)

    fun getEndedEventDuration(): Duration? =
        previousEvent?.let { previousEvent ->
            if (statusNotEquals(previousEvent)) {
                previousEvent.startedAt.diffToDuration(dispatchedAt)
            } else null
        }

    fun runWhenStateChanges(toRun: (UptimeMonitorEvent) -> Unit) =
        previousEvent?.let { previousEvent ->
            if (statusNotEquals(previousEvent)) {
                toRun(this)
            }
        } ?: toRun(this)

    private fun statusEquals(previousEvent: UptimeEventPojo) = uptimeStatus == previousEvent.status
}

data class MonitorUpEvent(
    override val monitor: MonitorPojo,
    val status: HttpStatus,
    val latency: Int,
    override val previousEvent: UptimeEventPojo?
) : UptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.UP

    override fun toStructuredMessage() =
        StructuredMonitorUpMessage(
            summary = "Your monitor \"${monitor.name}\" (${monitor.url}) is UP (${status.code})",
            latency = "Latency: ${latency}ms",
            previousDownTime = getEndedEventDuration().toDurationString()?.let { "Was down for $it" }
        )
}

data class MonitorDownEvent(
    override val monitor: MonitorPojo,
    val status: HttpStatus?,
    val error: Throwable,
    override val previousEvent: UptimeEventPojo?
) : UptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.DOWN

    override fun toStructuredMessage() =
        StructuredMonitorDownMessage(
            summary = "Your monitor \"${monitor.name}\" (${monitor.url}) is DOWN" +
                    (status?.let { " (" + it.code + ")" } ?: ""),
            error = "Reason: ${error.message}",
            previousUpTime = getEndedEventDuration().toDurationString()?.let { "Was up for $it" }
        )
}

data class RedirectEvent(
    override val monitor: MonitorPojo,
    val redirectLocation: URI
) : MonitorEvent() {

    override fun toStructuredMessage() = StructuredRedirectMessage(
        summary = "Request to \"${monitor.name}\" (${monitor.url}) has been redirected"
    )
}

sealed class SSLMonitorEvent : MonitorEvent() {
    abstract val previousEvent: SslEventPojo?

    abstract val sslStatus: SslStatus

    fun statusNotEquals(previousEvent: SslEventPojo) = !statusEquals(previousEvent)

    fun getEndedEventDuration(): Duration? =
        previousEvent?.let { previousEvent ->
            if (statusNotEquals(previousEvent)) {
                previousEvent.startedAt.diffToDuration(dispatchedAt)
            } else null
        }

    fun getPreviousStatusString(): String = previousEvent?.status?.name ?: ""

    fun runWhenStateChanges(toRun: (SSLMonitorEvent) -> Unit) =
        previousEvent?.let { previousEvent ->
            if (statusNotEquals(previousEvent)) {
                toRun(this)
            }
        } ?: toRun(this)

    private fun statusEquals(previousEvent: SslEventPojo) = sslStatus == previousEvent.status
}

data class SSLValidEvent(
    override val monitor: MonitorPojo,
    val certInfo: CertificateInfo,
    override val previousEvent: SslEventPojo?
) : SSLMonitorEvent() {

    override val sslStatus = SslStatus.VALID

    override fun toStructuredMessage() =
        StructuredSSLValidMessage(
            summary = "Your site \"${monitor.name}\" (${monitor.url}) has a VALID certificate",
            previousInvalidEvent = getEndedEventDuration().toDurationString()
                ?.let { "Was ${getPreviousStatusString()} for $it" }
        )
}

data class SSLInvalidEvent(
    override val monitor: MonitorPojo,
    val error: SSLValidationError,
    override val previousEvent: SslEventPojo?
) : SSLMonitorEvent() {

    override val sslStatus = SslStatus.INVALID

    override fun toStructuredMessage() =
        StructuredSSLInvalidMessage(
            summary = "Your site \"${monitor.name}\" (${monitor.url}) has an INVALID certificate",
            error = "Reason: ${error.message}",
            previousValidEvent = getEndedEventDuration().toDurationString()
                ?.let { "Was ${getPreviousStatusString()} for $it" }
        )
}

data class SSLWillExpireEvent(
    override val monitor: MonitorPojo,
    val certInfo: CertificateInfo,
    override val previousEvent: SslEventPojo?
) : SSLMonitorEvent() {

    override val sslStatus = SslStatus.WILL_EXPIRE

    override fun toStructuredMessage() =
        StructuredSSLWillExpireMessage(
            summary = "Your SSL certificate for ${monitor.url} will expire soon",
            validUntil = "Expiry date: ${certInfo.validTo}"
        )
}
