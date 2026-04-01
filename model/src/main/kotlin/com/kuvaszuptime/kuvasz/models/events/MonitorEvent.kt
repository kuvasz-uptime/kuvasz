package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent.Companion.ERROR_MAX_LENGTH
import com.kuvaszuptime.kuvasz.models.monitor.http.safeDisplayUrl
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import java.net.URI

sealed class MonitorEvent<M : MonitorRecord> {
    abstract val monitor: M

    abstract fun toStructuredMessage(): StructuredMessage

    val dispatchedAt = getCurrentTimestamp()

    companion object {
        const val ERROR_MAX_LENGTH = 255
    }
}

data class HttpRedirectEvent(
    override val monitor: HttpMonitorRecord,
    val redirectLocation: URI,
) : MonitorEvent<HttpMonitorRecord>() {

    override fun toStructuredMessage() = StructuredRedirectMessage(
        summary = Messages.requestHasBeenRedirected(monitor.name, monitor.safeDisplayUrl, redirectLocation),
    )
}

/**
 * Sanitizes a string by:
 * - Replacing null characters with the string "null"
 * - Removing all ISO control characters
 * - Truncating the string to a maximum length defined by [ERROR_MAX_LENGTH]
 * - Appending a redaction notice if the string was truncated
 *
 * Useful for external inputs that are out of our control, such as HTTP error messages.
 */
fun String.sanitizeAsError(): String {
    val sanitized = replace("\u0000", "null").filter { !it.isISOControl() }

    return if (sanitized.length > ERROR_MAX_LENGTH) {
        Messages.redacted(sanitized.take(ERROR_MAX_LENGTH))
    } else {
        sanitized
    }
}
