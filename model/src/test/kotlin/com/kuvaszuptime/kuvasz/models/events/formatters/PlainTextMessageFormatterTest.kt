package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
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

class PlainTextMessageFormatterTest : BehaviorSpec(
    {
        val formatter = PlainTextMessageFormatter

        val httpMonitor = HttpMonitorRecord()
            .setId(1111)
            .setName("test_monitor")
            .setUrl("https://test.url")
            .setSensitiveUrl(false)

        given("toFormattedMessage(event: UptimeMonitorEvent) - HTTP") {

            `when`("it gets an HttpMonitorUpEvent without a previousEvent") {
                val event = HttpMonitorUpEvent(httpMonitor, HttpStatus.OK, 300, null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_monitor\" (https://test.url) is UP (200)\nLatency: 300ms"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("the URL is sensitive") {
                val sensitiveMonitor = httpMonitor.copy().apply { setSensitiveUrl(true) }
                val event = HttpMonitorUpEvent(sensitiveMonitor, HttpStatus.OK, 300, null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_monitor\" (MASKED URL) is UP (200)\nLatency: 300ms"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an HttpMonitorUpEvent with a previousEvent with the same status") {
                val previousEvent = HttpUptimeEventRecord().setStatus(UptimeStatus.UP)
                val event = HttpMonitorUpEvent(httpMonitor, HttpStatus.OK, 300, previousEvent)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_monitor\" (https://test.url) is UP (200)\nLatency: 300ms"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an HttpMonitorUpEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = HttpUptimeEventRecord().setStatus(UptimeStatus.DOWN).setStartedAt(previousStartedAt)
                val event = HttpMonitorUpEvent(httpMonitor, HttpStatus.OK, 300, previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your monitor \"test_monitor\" (https://test.url) is UP (200)\nLatency: 300ms\n" +
                            "Was down for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an HttpMonitorDownEvent without a status") {
                val event = HttpMonitorDownEvent(httpMonitor, null, Exception("uptime error"), null)

                then("it should use the error message as a reason") {
                    val expectedMessage =
                        "Your monitor \"test_monitor\" (https://test.url) is DOWN\nReason: uptime error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an HttpMonitorDownEvent without a previousEvent") {
                val event = HttpMonitorDownEvent(httpMonitor, HttpStatus.BAD_REQUEST, Exception("uptime error"), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_monitor\" (https://test.url) is DOWN (400)\nReason: 400 Bad Request: " +
                            "uptime error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an HttpMonitorDownEvent with a previousEvent with the same status") {
                val previousEvent = HttpUptimeEventRecord().setStatus(UptimeStatus.DOWN)
                val event = HttpMonitorDownEvent(
                    httpMonitor,
                    HttpStatus.BAD_REQUEST,
                    Exception("uptime error"),
                    previousEvent
                )

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_monitor\" (https://test.url) is DOWN (400)\nReason: 400 Bad Request: " +
                            "uptime error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an HttpMonitorDownEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = HttpUptimeEventRecord().setStatus(UptimeStatus.UP).setStartedAt(previousStartedAt)
                val event = HttpMonitorDownEvent(
                    httpMonitor,
                    HttpStatus.BAD_REQUEST,
                    Exception("uptime error"),
                    previousEvent
                )

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your monitor \"test_monitor\" (https://test.url) is DOWN (400)\nReason: 400 Bad Request: " +
                            "uptime error\nWas up for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }

        given("toFormattedMessage(event: UptimeMonitorEvent) - Push") {

            val pushMonitor = PushMonitorRecord().apply {
                id = 222
                name = "test_push_monitor"
            }

            `when`("it gets a PushMonitorUpEvent without a previousEvent") {
                val event = PushMonitorUpEvent(pushMonitor, null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_push_monitor\" is UP"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a PushMonitorUpEvent with a previousEvent with the same status") {
                val previousEvent = PushUptimeEventRecord().setStatus(UptimeStatus.UP)
                val event = PushMonitorUpEvent(pushMonitor, previousEvent)

                then("it should return the correct message") {
                    val expectedMessage = "Your monitor \"test_push_monitor\" is UP"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a PushMonitorUpEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = PushUptimeEventRecord().setStatus(UptimeStatus.DOWN).setStartedAt(previousStartedAt)
                val event = PushMonitorUpEvent(pushMonitor, previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your monitor \"test_push_monitor\" is UP\n" +
                            "Was down for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a PushMonitorDownEvent without a status") {
                val event = PushMonitorDownEvent(pushMonitor, "missed a heartbeat", null)

                then("it should use the error message as a reason") {
                    val expectedMessage =
                        "Your monitor \"test_push_monitor\" is DOWN\nReason: missed a heartbeat"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a PushMonitorDownEvent without a previousEvent") {
                val event = PushMonitorDownEvent(pushMonitor, "missed a heartbeat", null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_push_monitor\" is DOWN\nReason: missed a heartbeat"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a PushMonitorDownEvent with a previousEvent with the same status") {
                val previousEvent = PushUptimeEventRecord().setStatus(UptimeStatus.DOWN)
                val event = PushMonitorDownEvent(pushMonitor, "uptime error", previousEvent)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_push_monitor\" is DOWN\nReason: uptime error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a PushMonitorDownEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = PushUptimeEventRecord().setStatus(UptimeStatus.UP).setStartedAt(previousStartedAt)
                val event = PushMonitorDownEvent(pushMonitor, "uptime error", previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your monitor \"test_push_monitor\" is DOWN\nReason: uptime error\n" +
                            "Was up for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }

        given("toFormattedMessage(event: SSLMonitorEvent)") {

            `when`("it gets an SSLValidEvent without a previousEvent") {
                val event = SSLValidEvent(httpMonitor, generateCertificateInfo(), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your site \"test_monitor\" (https://test.url) has a VALID certificate"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLValidEvent with a previousEvent with the same status") {
                val previousEvent = SslEventRecord().setStatus(SslStatus.VALID)
                val event = SSLValidEvent(httpMonitor, generateCertificateInfo(), previousEvent)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your site \"test_monitor\" (https://test.url) has a VALID certificate"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLValidEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = SslEventRecord().setStatus(SslStatus.INVALID).setStartedAt(previousStartedAt)
                val event = SSLValidEvent(httpMonitor, generateCertificateInfo(), previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your site \"test_monitor\" (https://test.url) has a VALID certificate\n" +
                            "Was INVALID for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLInvalidEvent without a previousEvent") {
                val event = SSLInvalidEvent(httpMonitor, SSLValidationError("ssl error"), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your site \"test_monitor\" (https://test.url) has an INVALID certificate\nReason: ssl error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLInvalidEvent with a previousEvent with the same status") {
                val previousEvent = SslEventRecord().setStatus(SslStatus.INVALID)
                val event = SSLInvalidEvent(httpMonitor, SSLValidationError("ssl error"), previousEvent)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your site \"test_monitor\" (https://test.url) has an INVALID certificate\nReason: ssl error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLInvalidEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = SslEventRecord().setStatus(SslStatus.VALID).setStartedAt(previousStartedAt)
                val event = SSLInvalidEvent(httpMonitor, SSLValidationError("ssl error"), previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your site \"test_monitor\" (https://test.url) has an INVALID certificate\n" +
                            "Reason: ssl error\nWas VALID for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an SSLWillExpireEvent") {
                val event = SSLWillExpireEvent(httpMonitor, generateCertificateInfo(), null)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your SSL certificate for \"test_monitor\" will expire soon\n" +
                            "Expiry date: ${event.certInfo.validTo}"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }
    }
)
