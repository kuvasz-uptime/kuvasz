package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.events.*
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.tables.records.UptimeEventRecord
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus

class TelegramTextFormatterTest : BehaviorSpec(
    {
        val formatter = TelegramTextFormatter

        val monitor = MonitorRecord()
            .setId(1111)
            .setName("test_monitor")
            .setUrl("https://test.url")

        given("toFormattedMessage(event: UptimeMonitorEvent)") {

            `when`("it gets a MonitorUpEvent without a previousEvent") {
                val event = MonitorUpEvent(monitor, HttpStatus.OK, 300, null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_monitor\" (https://test.url) is UP (200)</b>\n<i>Latency: 300ms</i>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorUpEvent with a previousEvent with the same status") {
                val previousEvent = UptimeEventRecord().setStatus(UptimeStatus.UP)
                val event = MonitorUpEvent(monitor, HttpStatus.OK, 300, previousEvent)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_monitor\" (https://test.url) is UP (200)</b>\n<i>Latency: 300ms</i>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorUpEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = UptimeEventRecord().setStatus(UptimeStatus.DOWN).setStartedAt(previousStartedAt)
                val event = MonitorUpEvent(monitor, HttpStatus.OK, 300, previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_monitor\" (https://test.url) is UP (200)</b>\n<i>Latency: 300ms" +
                            "</i>\nWas down for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorDownEvent without a previousEvent") {
                val event = MonitorDownEvent(monitor, HttpStatus.BAD_REQUEST, Throwable("uptime error"), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "🚨 <b>Your monitor \"test_monitor\" (https://test.url) is DOWN (400)</b>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorDownEvent with a previousEvent with the same status") {
                val previousEvent = UptimeEventRecord().setStatus(UptimeStatus.DOWN)
                val event = MonitorDownEvent(
                    monitor,
                    HttpStatus.BAD_REQUEST,
                    Throwable("uptime error"),
                    previousEvent
                )

                then("it should return the correct message") {
                    val expectedMessage =
                        "🚨 <b>Your monitor \"test_monitor\" (https://test.url) is DOWN (400)</b>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorDownEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = UptimeEventRecord().setStatus(UptimeStatus.UP).setStartedAt(previousStartedAt)
                val event = MonitorDownEvent(
                    monitor,
                    HttpStatus.BAD_REQUEST,
                    Throwable("uptime error"),
                    previousEvent
                )

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "🚨 <b>Your monitor \"test_monitor\" (https://test.url) is DOWN (400)</b>\n" +
                            "Was up for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }

        given("toFormattedMessage(event: SSLMonitorEvent)") {

            `when`("it gets an SSLValidEvent without a previousEvent") {
                val event = SSLValidEvent(monitor, generateCertificateInfo(), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "\uD83D\uDD12️ <b>Your site \"test_monitor\" (https://test.url) has a VALID certificate</b>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLValidEvent with a previousEvent with the same status") {
                val previousEvent = SslEventRecord().setStatus(SslStatus.VALID)
                val event = SSLValidEvent(monitor, generateCertificateInfo(), previousEvent)

                then("it should return the correct message") {
                    val expectedMessage =
                        "\uD83D\uDD12️ <b>Your site \"test_monitor\" (https://test.url) has a VALID certificate</b>"
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
                        "\uD83D\uDD12️ <b>Your site \"test_monitor\" (https://test.url) has a VALID certificate</b>\n" +
                            "Was INVALID for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLInvalidEvent without a previousEvent") {
                val event = SSLInvalidEvent(monitor, SSLValidationError("ssl error"), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "🚨 <b>Your site \"test_monitor\" (https://test.url) has an INVALID " +
                            "certificate</b>\n<i>Reason: ssl error</i>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLInvalidEvent with a previousEvent with the same status") {
                val previousEvent = SslEventRecord().setStatus(SslStatus.INVALID)
                val event = SSLInvalidEvent(monitor, SSLValidationError("ssl error"), previousEvent)

                then("it should return the correct message") {
                    val expectedMessage = "🚨 <b>Your site \"test_monitor\" (https://test.url) has an INVALID " +
                        "certificate</b>\n<i>Reason: ssl error</i>"
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
                        "🚨 <b>Your site \"test_monitor\" (https://test.url) has an INVALID certificate</b>\n" +
                            "<i>Reason: ssl error</i>\nWas VALID for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLWillExpireEvent") {
                val event = SSLWillExpireEvent(monitor, generateCertificateInfo(), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "⚠️ <b>Your SSL certificate for https://test.url will expire soon</b>\n" +
                            "<i>Expiry date: ${event.certInfo.validTo}</i>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }
    }
)
