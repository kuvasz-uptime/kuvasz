package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.models.CertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import java.net.URI
import java.time.OffsetDateTime

class LogMessageFormatterTest : BehaviorSpec({

    val formatter = LogMessageFormatter

    val monitor = HttpMonitorRecord()
        .setId(1111)
        .setName("test_monitor")
        .setUrl("https://test.url")

    given("toFormattedMessage(event: UptimeMonitorEvent)") {

        `when`("it gets a MonitorUpEvent without a previousEvent") {
            val event = HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null)

            then("it should return the correct message") {
                val expectedMessage =
                    "✅ Your monitor \"test_monitor\" (https://test.url) is UP (200). Latency: 300ms"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets a MonitorUpEvent with a previousEvent with the same status") {
            val previousEvent = HttpUptimeEventRecord().setStatus(UptimeStatus.UP)
            val event = HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, previousEvent)

            then("it should return the correct message") {
                val expectedMessage =
                    "✅ Your monitor \"test_monitor\" (https://test.url) is UP (200). Latency: 300ms"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets a MonitorUpEvent with a previousEvent with different status") {
            val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
            val previousEvent = HttpUptimeEventRecord().setStatus(UptimeStatus.DOWN).setStartedAt(previousStartedAt)
            val event = HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, previousEvent)

            then("it should return the correct message") {
                val expectedDurationString =
                    previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                val expectedMessage =
                    "✅ Your monitor \"test_monitor\" (https://test.url) is UP (200). Latency: 300ms. " +
                        "Was down for $expectedDurationString"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets a MonitorDownEvent without a response status") {
            val event = HttpMonitorDownEvent(monitor, null, Exception("uptime error"), null)

            then("it should use the error message as a reason") {
                val expectedMessage =
                    "🚨 Your monitor \"test_monitor\" (https://test.url) is DOWN. Reason: uptime error"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets a MonitorDownEvent without a previousEvent") {
            val event = HttpMonitorDownEvent(monitor, HttpStatus.BAD_REQUEST, Exception("uptime error"), null)

            then("it should return the correct message") {
                val expectedMessage =
                    "🚨 Your monitor \"test_monitor\" (https://test.url) is DOWN (400). Reason: 400 Bad Request: " +
                        "uptime error"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets a MonitorDownEvent with a previousEvent with the same status") {
            val previousEvent = HttpUptimeEventRecord().setStatus(UptimeStatus.DOWN)
            val event = HttpMonitorDownEvent(
                monitor,
                HttpStatus.BAD_REQUEST,
                Exception("uptime error"),
                previousEvent
            )

            then("it should return the correct message") {
                val expectedMessage =
                    "🚨 Your monitor \"test_monitor\" (https://test.url) is DOWN (400). Reason: 400 Bad Request: " +
                        "uptime error"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets a MonitorDownEvent with a previousEvent with different status") {
            val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
            val previousEvent = HttpUptimeEventRecord().setStatus(UptimeStatus.UP).setStartedAt(previousStartedAt)
            val event = HttpMonitorDownEvent(
                monitor,
                HttpStatus.BAD_REQUEST,
                Exception("uptime error"),
                previousEvent
            )

            then("it should return the correct message") {
                val expectedDurationString =
                    previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                val expectedMessage =
                    "🚨 Your monitor \"test_monitor\" (https://test.url) is DOWN (400). Reason: 400 Bad Request: " +
                        "uptime error. Was up for $expectedDurationString"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }
    }

    given("toFormattedMessage(event: RedirectEvent)") {

        `when`("it gets a RedirectEvent") {
            val event = HttpRedirectEvent(monitor, URI("https://irrelevant.com"))

            then("it should return the correct message") {
                val expectedMessage = "ℹ️ Request to \"test_monitor\" (https://test.url) has been redirected " +
                    "to https://irrelevant.com"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }
    }

    given("toFormattedMessage(event: SSLMonitorEvent)") {

        `when`("it gets an SSLValidEvent without a previousEvent") {
            val event = SSLValidEvent(monitor, generateCertificateInfo(), null)

            then("it should return the correct message") {
                val expectedMessage =
                    "\uD83D\uDD12️ Your site \"test_monitor\" (https://test.url) has a VALID certificate"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets an SSLValidEvent with a previousEvent with the same status") {
            val previousEvent = SslEventRecord().setStatus(SslStatus.VALID)
            val event = SSLValidEvent(monitor, generateCertificateInfo(), previousEvent)

            then("it should return the correct message") {
                val expectedMessage =
                    "\uD83D\uDD12️ Your site \"test_monitor\" (https://test.url) has a VALID certificate"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets an SSLValidEvent with a previousEvent with different status") {
            val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
            val previousEvent = SslEventRecord().setStatus(SslStatus.INVALID).setStartedAt(previousStartedAt)
            val event = SSLValidEvent(monitor, generateCertificateInfo(), previousEvent)

            then("it should return the correct message") {
                val expectedDurationString =
                    previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                val expectedMessage =
                    "\uD83D\uDD12️ Your site \"test_monitor\" (https://test.url) has a VALID certificate. " +
                        "Was INVALID for $expectedDurationString"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets an SSLInvalidEvent without a previousEvent") {
            val event = SSLInvalidEvent(monitor, SSLValidationError("ssl error"), null)

            then("it should return the correct message") {
                val expectedMessage =
                    "🚨 Your site \"test_monitor\" (https://test.url) has an INVALID certificate. Reason: ssl error"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets an SSLInvalidEvent with a previousEvent with the same status") {
            val previousEvent = SslEventRecord().setStatus(SslStatus.INVALID)
            val event = SSLInvalidEvent(monitor, SSLValidationError("ssl error"), previousEvent)

            then("it should return the correct message") {
                val expectedMessage =
                    "🚨 Your site \"test_monitor\" (https://test.url) has an INVALID certificate. Reason: ssl error"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets an SSLInvalidEvent with a previousEvent with different status") {
            val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
            val previousEvent = SslEventRecord().setStatus(SslStatus.VALID).setStartedAt(previousStartedAt)
            val event = SSLInvalidEvent(monitor, SSLValidationError("ssl error"), previousEvent)

            then("it should return the correct message") {
                val expectedDurationString =
                    previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                val expectedMessage =
                    "🚨 Your site \"test_monitor\" (https://test.url) has an INVALID certificate. " +
                        "Reason: ssl error. Was VALID for $expectedDurationString"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }

        `when`("it gets an SSLWillExpireEvent") {
            val event = SSLWillExpireEvent(monitor, generateCertificateInfo(), null)

            then("it should return the correct message") {
                val expectedMessage =
                    "⚠️ Your SSL certificate for https://test.url will expire soon. " +
                        "Expiry date: ${event.certInfo.validTo}"
                formatter.toFormattedMessage(event) shouldBe expectedMessage
            }
        }
    }
})

fun generateCertificateInfo(validTo: OffsetDateTime = getCurrentTimestamp().plusDays(60)) =
    CertificateInfo(validTo)
