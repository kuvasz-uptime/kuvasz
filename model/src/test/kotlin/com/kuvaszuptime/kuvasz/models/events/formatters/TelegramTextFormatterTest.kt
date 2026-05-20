package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus

class TelegramTextFormatterTest : BehaviorSpec(
    {
        val formatter = TelegramTextFormatter

        val monitor = HttpMonitorRecord()
            .setId(1111)
            .setName("test_monitor")
            .setUrl("https://test.url")
            .setSensitiveUrl(false)

        given("toFormattedMessage(event: UptimeMonitorEvent)") {

            `when`("it gets a MonitorUpEvent without a previousEvent") {
                val event = HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_monitor\" (https://test.url) is UP (200)</b>\n<i>Latency: 300ms</i>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("the URL is sensitive") {
                val sensitiveMonitor = monitor.copy().apply { setSensitiveUrl(true) }
                val event = HttpMonitorUpEvent(sensitiveMonitor, HttpStatus.OK, 300, null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_monitor\" (MASKED URL) is UP (200)</b>\n<i>Latency: 300ms</i>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorUpEvent with a previousEvent with the same status") {
                val previousEvent = HttpUptimeEventRecord().setStatus(UptimeStatus.UP)
                val event = HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, previousEvent)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_monitor\" (https://test.url) is UP (200)</b>\n<i>Latency: 300ms</i>"
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
                        "✅ <b>Your monitor \"test_monitor\" (https://test.url) is UP (200)</b>\n<i>Latency: 300ms" +
                            "</i>\nWas down for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a MonitorDownEvent without a previousEvent") {
                val event = HttpMonitorDownEvent(monitor, HttpStatus.BAD_REQUEST, Exception("uptime error"), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "🚨 <b>Your monitor \"test_monitor\" (https://test.url) is DOWN (400)</b>"
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
                        "🚨 <b>Your monitor \"test_monitor\" (https://test.url) is DOWN (400)</b>"
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
                        "🚨 <b>Your monitor \"test_monitor\" (https://test.url) is DOWN (400)</b>\n" +
                            "Was up for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }

        given("toFormattedMessage(event: UptimeMonitorEvent) - ICMP") {

            val icmpMonitor = IcmpMonitorRecord()
                .setId(3333L)
                .setName("test_icmp_monitor")
                .setHost("example.com")

            `when`("it gets an IcmpMonitorUpEvent without a previousEvent") {
                val event = IcmpMonitorUpEvent(icmpMonitor, null, 300, 0)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_icmp_monitor\" is UP</b>\n<i>Avg. latency: 300 ms</i>\nPacket loss: 0%"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorUpEvent with null latency") {
                val event = IcmpMonitorUpEvent(icmpMonitor, null, null, 0)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_icmp_monitor\" is UP</b>\nPacket loss: 0%"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorUpEvent with a previousEvent with the same status") {
                val previousEvent = IcmpUptimeEventRecord().setStatus(UptimeStatus.UP)
                val event = IcmpMonitorUpEvent(icmpMonitor, previousEvent, 300, 0)

                then("it should return the correct message") {
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_icmp_monitor\" is UP</b>\n<i>Avg. latency: 300 ms</i>\nPacket loss: 0%"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorUpEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = IcmpUptimeEventRecord().setStatus(UptimeStatus.DOWN).setStartedAt(previousStartedAt)
                val event = IcmpMonitorUpEvent(icmpMonitor, previousEvent, 300, 0)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "✅ <b>Your monitor \"test_icmp_monitor\" is UP</b>\n<i>Avg. latency: 300 ms</i>\n" +
                            "Packet loss: 0%\nWas down for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorDownEvent without a previousEvent") {
                val event = IcmpMonitorDownEvent(icmpMonitor, "icmp error", null, 100)

                then("it should return the correct message") {
                    val expectedMessage =
                        "🚨 <b>Your monitor \"test_icmp_monitor\" is DOWN</b>\nPacket loss: 100%"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorDownEvent with a previousEvent with the same status") {
                val previousEvent = IcmpUptimeEventRecord().setStatus(UptimeStatus.DOWN)
                val event = IcmpMonitorDownEvent(icmpMonitor, "icmp error", previousEvent, 100)

                then("it should return the correct message") {
                    val expectedMessage =
                        "🚨 <b>Your monitor \"test_icmp_monitor\" is DOWN</b>\nPacket loss: 100%"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorDownEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = IcmpUptimeEventRecord().setStatus(UptimeStatus.UP).setStartedAt(previousStartedAt)
                val event = IcmpMonitorDownEvent(icmpMonitor, "icmp error", previousEvent, 100)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "🚨 <b>Your monitor \"test_icmp_monitor\" is DOWN</b>\nPacket loss: 100%\n" +
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
                        "⚠️ <b>Your SSL certificate for \"test_monitor\" will expire soon</b>\n" +
                            "<i>Expiry date: ${event.certInfo.validTo}</i>"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }
    }
)
