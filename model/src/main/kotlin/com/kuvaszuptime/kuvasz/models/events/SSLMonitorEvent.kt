package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.toDurationString
import kotlin.time.Duration

sealed class SSLMonitorEvent : MonitorEvent<HttpMonitorRecord>() {
    abstract val previousEvent: SslEventRecord?

    abstract val sslStatus: SslStatus

    fun statusNotEquals(previousEvent: SslEventRecord) = !statusEquals(previousEvent)

    fun getEndedEventDuration(): Duration? =
        previousEvent?.let { previousEvent ->
            if (statusNotEquals(previousEvent)) {
                previousEvent.startedAt.diffToDuration(dispatchedAt)
            } else {
                null
            }
        }

    fun getPreviousStatusString(): String = previousEvent?.status?.name.orEmpty()

    fun runWhenStateChanges(toRun: (SSLMonitorEvent) -> Unit) =
        previousEvent?.let { previousEvent ->
            if (statusNotEquals(previousEvent)) {
                toRun(this)
            }
        } ?: toRun(this)

    private fun statusEquals(previousEvent: SslEventRecord) = sslStatus == previousEvent.status
}

interface WithCertInfo {
    val monitor: HttpMonitorRecord
    val certInfo: CertificateInfo
}

data class SSLValidEvent(
    override val monitor: HttpMonitorRecord,
    override val certInfo: CertificateInfo,
    override val previousEvent: SslEventRecord?
) : SSLMonitorEvent(), WithCertInfo {

    override val sslStatus = SslStatus.VALID

    override fun toStructuredMessage() =
        StructuredSSLValidMessage(
            summary = Messages.yourSiteHasAValidCert(monitor.name, monitor.url),
            previousInvalidEvent = getEndedEventDuration().toDurationString()
                ?.let { Messages.wasXForY(getPreviousStatusString(), it) }
        )
}

data class SSLInvalidEvent(
    override val monitor: HttpMonitorRecord,
    val error: SSLValidationError,
    override val previousEvent: SslEventRecord?
) : SSLMonitorEvent() {

    override val sslStatus = SslStatus.INVALID

    override fun toStructuredMessage() =
        StructuredSSLInvalidMessage(
            summary = Messages.yourSiteHasAnInvalidCert(monitor.name, monitor.url),
            error = Messages.reasonExplanation(error.message?.sanitizeAsError().orEmpty()),
            previousValidEvent = getEndedEventDuration().toDurationString()
                ?.let { Messages.wasXForY(getPreviousStatusString(), it) }
        )
}

data class SSLWillExpireEvent(
    override val monitor: HttpMonitorRecord,
    override val certInfo: CertificateInfo,
    override val previousEvent: SslEventRecord?
) : SSLMonitorEvent(), WithCertInfo {

    override val sslStatus = SslStatus.WILL_EXPIRE

    override fun toStructuredMessage() =
        StructuredSSLWillExpireMessage(
            summary = Messages.yourCertWillExpireSoon(monitor.url),
            validUntil = Messages.expiryDate(certInfo.validTo)
        )
}
