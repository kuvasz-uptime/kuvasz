package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.tables.pojos.SslEventPojo
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus

class SlackTextFormatterTest : BehaviorSpec(
    {
        val formatter = SlackTextFormatter

        val monitor = MonitorPojo()
            .setId(1111)
            .setName("test_monitor")
            .setUrl("https://test.url")

        given("toFormattedMessage(event: UptimeMonitorEvent)") {

            `when`("it gets a MonitorUpEvent without a previousEvent") {
                val event = MonitorUpEvent(monitor, HttpStatus.OK, 300, null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ *Your monitor \"test_monitor\" (https://test.url) is UP (200)*\n_Latency: 300ms_"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorUpEvent with a previousEvent with the same status") {
                val previousEvent = UptimeEventPojo().setStatus(UptimeStatus.UP)
                val event = MonitorUpEvent(monitor, HttpStatus.OK, 300, previousEvent)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ *Your monitor \"test_monitor\" (https://test.url) is UP (200)*\n_Latency: 300ms_"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorUpEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = UptimeEventPojo().setStatus(UptimeStatus.DOWN).setStartedAt(previousStartedAt)
                val event = MonitorUpEvent(monitor, HttpStatus.OK, 300, previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "✅ *Your monitor \"test_monitor\" (https://test.url) is UP (200)*\n_Latency: 300ms_\n" +
                            "Was down for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorDownEvent without a previousEvent") {
                val event = MonitorDownEvent(monitor, HttpStatus.BAD_REQUEST, Throwable("uptime error"), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "🚨 *Your monitor \"test_monitor\" (https://test.url) is DOWN (400)*"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorDownEvent with a previousEvent with the same status") {
                val previousEvent = UptimeEventPojo().setStatus(UptimeStatus.DOWN)
                val event = MonitorDownEvent(
                    monitor,
                    HttpStatus.BAD_REQUEST,
                    Throwable("uptime error"),
                    previousEvent
                )

                then("it should return the correct message") {
                    val expectedMessage =
                        "🚨 *Your monitor \"test_monitor\" (https://test.url) is DOWN (400)*"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorDownEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = UptimeEventPojo().setStatus(UptimeStatus.UP).setStartedAt(previousStartedAt)
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
                        "🚨 *Your monitor \"test_monitor\" (https://test.url) is DOWN (400)*\n" +
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
                        "\uD83D\uDD12️ *Your site \"test_monitor\" (https://test.url) has a VALID certificate*"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLValidEvent with a previousEvent with the same status") {
                val previousEvent = SslEventPojo().setStatus(SslStatus.VALID)
                val event = SSLValidEvent(monitor, generateCertificateInfo(), previousEvent)

                then("it should return the correct message") {
                    val expectedMessage =
                        "\uD83D\uDD12️ *Your site \"test_monitor\" (https://test.url) has a VALID certificate*"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLValidEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = SslEventPojo().setStatus(SslStatus.INVALID).setStartedAt(previousStartedAt)
                val event = SSLValidEvent(monitor, generateCertificateInfo(), previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "\uD83D\uDD12️ *Your site \"test_monitor\" (https://test.url) has a VALID certificate*\n" +
                            "Was INVALID for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLInvalidEvent without a previousEvent") {
                val event = SSLInvalidEvent(monitor, SSLValidationError("ssl error"), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "🚨 *Your site \"test_monitor\" (https://test.url) has an INVALID " +
                            "certificate*\n_Reason: ssl error_"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLInvalidEvent with a previousEvent with the same status") {
                val previousEvent = SslEventPojo().setStatus(SslStatus.INVALID)
                val event = SSLInvalidEvent(monitor, SSLValidationError("ssl error"), previousEvent)

                then("it should return the correct message") {
                    val expectedMessage = "🚨 *Your site \"test_monitor\" (https://test.url) has an INVALID " +
                        "certificate*\n_Reason: ssl error_"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLInvalidEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = SslEventPojo().setStatus(SslStatus.VALID).setStartedAt(previousStartedAt)
                val event = SSLInvalidEvent(monitor, SSLValidationError("ssl error"), previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "🚨 *Your site \"test_monitor\" (https://test.url) has an INVALID certificate*\n" +
                            "_Reason: ssl error_\nWas VALID for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLWillExpireEvent") {
                val event = SSLWillExpireEvent(monitor, generateCertificateInfo(), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "⚠️ *Your SSL certificate for https://test.url will expire soon*\n" +
                            "_Expiry date: ${event.certInfo.validTo}_"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }
    }
)
