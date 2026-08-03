package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
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

        given("toFormattedMessage(event: UptimeMonitorEvent) - ICMP") {

            val icmpMonitor = IcmpMonitorRecord()
                .setId(3333L)
                .setName("test_icmp_monitor")
                .setHost("example.com")

            `when`("it gets an IcmpMonitorUpEvent without a previousEvent") {
                val event = IcmpMonitorUpEvent(icmpMonitor, null, 300, 0)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_icmp_monitor\" is UP\nAvg. latency: 300 ms\nPacket loss: 0%"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorUpEvent with null latency") {
                val event = IcmpMonitorUpEvent(icmpMonitor, null, null, 50)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_icmp_monitor\" is UP\nPacket loss: 50%"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorUpEvent with a previousEvent with the same status") {
                val previousEvent = IcmpUptimeEventRecord().setStatus(UptimeStatus.UP)
                val event = IcmpMonitorUpEvent(icmpMonitor, previousEvent, 300, 0)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_icmp_monitor\" is UP\nAvg. latency: 300 ms\nPacket loss: 0%"
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
                        "Your monitor \"test_icmp_monitor\" is UP\nAvg. latency: 300 ms\nPacket loss: 0%\n" +
                            "Was down for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorDownEvent without a previousEvent") {
                val event = IcmpMonitorDownEvent(icmpMonitor, "icmp error", null, 100)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_icmp_monitor\" is DOWN\nReason: icmp error\nPacket loss: 100%"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets an IcmpMonitorDownEvent with a previousEvent with the same status") {
                val previousEvent = IcmpUptimeEventRecord().setStatus(UptimeStatus.DOWN)
                val event = IcmpMonitorDownEvent(icmpMonitor, "icmp error", previousEvent, 100)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_icmp_monitor\" is DOWN\nReason: icmp error\nPacket loss: 100%"
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
                        "Your monitor \"test_icmp_monitor\" is DOWN\nReason: icmp error\nPacket loss: 100%\n" +
                            "Was up for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }

        given("toFormattedMessage(event: UptimeMonitorEvent) - TCP") {

            val tcpMonitor = TcpMonitorRecord()
                .setId(4444L)
                .setName("test_tcp_monitor")
                .setHost("example.com")

            `when`("it gets a TcpMonitorUpEvent without a previousEvent") {
                val event = TcpMonitorUpEvent(tcpMonitor, null, 300)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_tcp_monitor\" is UP\nConnect latency: 300 ms"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a TcpMonitorUpEvent with null latency") {
                val event = TcpMonitorUpEvent(tcpMonitor, null, null)

                then("it should return the correct message") {
                    val expectedMessage = "Your monitor \"test_tcp_monitor\" is UP"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a TcpMonitorUpEvent with a previousEvent with the same status") {
                val previousEvent = TcpUptimeEventRecord().setStatus(UptimeStatus.UP)
                val event = TcpMonitorUpEvent(tcpMonitor, previousEvent, 300)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_tcp_monitor\" is UP\nConnect latency: 300 ms"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a TcpMonitorUpEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = TcpUptimeEventRecord().setStatus(UptimeStatus.DOWN).setStartedAt(previousStartedAt)
                val event = TcpMonitorUpEvent(tcpMonitor, previousEvent, 300)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your monitor \"test_tcp_monitor\" is UP\nConnect latency: 300 ms\n" +
                            "Was down for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a TcpMonitorDownEvent without a previousEvent") {
                val event = TcpMonitorDownEvent(tcpMonitor, "tcp error", null)

                then("it should return the correct message") {
                    val expectedMessage = "Your monitor \"test_tcp_monitor\" is DOWN\nReason: tcp error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a TcpMonitorDownEvent with a previousEvent with the same status") {
                val previousEvent = TcpUptimeEventRecord().setStatus(UptimeStatus.DOWN)
                val event = TcpMonitorDownEvent(tcpMonitor, "tcp error", previousEvent)

                then("it should return the correct message") {
                    val expectedMessage = "Your monitor \"test_tcp_monitor\" is DOWN\nReason: tcp error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a TcpMonitorDownEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = TcpUptimeEventRecord().setStatus(UptimeStatus.UP).setStartedAt(previousStartedAt)
                val event = TcpMonitorDownEvent(tcpMonitor, "tcp error", previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your monitor \"test_tcp_monitor\" is DOWN\nReason: tcp error\n" +
                            "Was up for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }

        given("toFormattedMessage(event: UptimeMonitorEvent) - DNS") {

            val dnsMonitor = DnsMonitorRecord()
                .setId(4444L)
                .setName("test_dns_monitor")
                .setHost("example.com")

            `when`("it gets a DnsMonitorUpEvent without a previousEvent") {
                val event = DnsMonitorUpEvent(dnsMonitor, null, 300)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_dns_monitor\" is UP\nResolution latency: 300 ms"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a DnsMonitorUpEvent with null latency") {
                val event = DnsMonitorUpEvent(dnsMonitor, null, null)

                then("it should return the correct message") {
                    val expectedMessage = "Your monitor \"test_dns_monitor\" is UP"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a DnsMonitorUpEvent with a previousEvent with the same status") {
                val previousEvent = DnsUptimeEventRecord().setStatus(UptimeStatus.UP)
                val event = DnsMonitorUpEvent(dnsMonitor, previousEvent, 300)

                then("it should return the correct message") {
                    val expectedMessage =
                        "Your monitor \"test_dns_monitor\" is UP\nResolution latency: 300 ms"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a DnsMonitorUpEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = DnsUptimeEventRecord().setStatus(UptimeStatus.DOWN).setStartedAt(previousStartedAt)
                val event = DnsMonitorUpEvent(dnsMonitor, previousEvent, 300)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your monitor \"test_dns_monitor\" is UP\nResolution latency: 300 ms\n" +
                            "Was down for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a DnsMonitorDownEvent without a previousEvent") {
                val event = DnsMonitorDownEvent(dnsMonitor, "dns error", null)

                then("it should return the correct message") {
                    val expectedMessage = "Your monitor \"test_dns_monitor\" is DOWN\nReason: dns error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a DnsMonitorDownEvent with a previousEvent with the same status") {
                val previousEvent = DnsUptimeEventRecord().setStatus(UptimeStatus.DOWN)
                val event = DnsMonitorDownEvent(dnsMonitor, "dns error", previousEvent)

                then("it should return the correct message") {
                    val expectedMessage = "Your monitor \"test_dns_monitor\" is DOWN\nReason: dns error"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }

            `when`("it gets a DnsMonitorDownEvent with a previousEvent with different status") {
                val previousStartedAt = getCurrentTimestamp().minusMinutes(30)
                val previousEvent = DnsUptimeEventRecord().setStatus(UptimeStatus.UP).setStartedAt(previousStartedAt)
                val event = DnsMonitorDownEvent(dnsMonitor, "dns error", previousEvent)

                then("it should return the correct message") {
                    val expectedDurationString =
                        previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()
                    val expectedMessage =
                        "Your monitor \"test_dns_monitor\" is DOWN\nReason: dns error\n" +
                            "Was up for $expectedDurationString"
                    formatter.toFormattedMessage(event) shouldBe expectedMessage
                }
            }
        }

        given("toFormattedMessage(event: DnsRecordsChangedEvent)") {
            val monitor = DnsMonitorRecord().setId(5555L).setName("drift_monitor")

            `when`("it gets a DnsRecordsChangedEvent") {
                val event = DnsRecordsChangedEvent(
                    monitor = monitor,
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )

                then("it should return the correct message") {
                    formatter.toFormattedMessage(event) shouldBe
                        "DNS records changed for monitor \"drift_monitor\"\nA: [1.1.1.1] → [2.2.2.2]"
                }
            }

            `when`("the changed records are longer than the allowed details length") {
                // A single TXT value (a DKIM key, for example) can be longer than a chat message is allowed to be
                val event = DnsRecordsChangedEvent(
                    monitor = monitor,
                    previousRecords = mapOf(DnsRecordType.TXT to listOf("v=dkim1; p=" + "a".repeat(2000))),
                    currentRecords = mapOf(DnsRecordType.TXT to listOf("v=dkim1; p=" + "b".repeat(2000))),
                )

                then("the diff should be truncated and marked as redacted") {
                    val details = event.toStructuredMessage().details

                    details.length shouldBe MonitorEvent.DETAILS_MAX_LENGTH + "... [REDACTED]".length
                    details shouldEndWith "... [REDACTED]"
                    details shouldStartWith "TXT: [v=dkim1; p=aaa"
                }
            }

            `when`("the changed records span more record types than fit into the details length") {
                val event = DnsRecordsChangedEvent(
                    monitor = monitor,
                    previousRecords = mapOf(
                        DnsRecordType.A to listOf("1.1.1.1"),
                        DnsRecordType.TXT to listOf("x".repeat(2000)),
                    ),
                    currentRecords = mapOf(
                        DnsRecordType.A to listOf("2.2.2.2"),
                        DnsRecordType.TXT to listOf("y".repeat(2000)),
                    ),
                )

                then("the entries that fit are kept, so a huge TXT does not hide the other types") {
                    val details = event.toStructuredMessage().details

                    details shouldStartWith "A: [1.1.1.1] → [2.2.2.2]\nTXT: ["
                    details shouldEndWith "... [REDACTED]"
                }
            }

            `when`("a record value contains ISO control characters") {
                val event = DnsRecordsChangedEvent(
                    monitor = monitor,
                    previousRecords = mapOf(DnsRecordType.TXT to listOf("old")),
                    currentRecords = mapOf(DnsRecordType.TXT to listOf("new\u0000\u0007")),
                )

                then("they should be stripped while the entry separators survive") {
                    event.toStructuredMessage().details shouldBe "TXT: [old] → [newnull]"
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

        given("toFormattedMaintenanceMessage(event: MaintenanceWindowEvent)") {

            val window = MaintenanceWindowRecord()
                .setId(99)
                .setName("test_window")
                .setEnabled(true)

            `when`("it gets a start event with a description") {
                val event = MaintenanceWindowStartEvent(window.copy().apply { setDescription("Scheduled DB upgrade") })

                then("it should return the plain summary followed by the description") {
                    formatter.toFormattedMessage(event) shouldBe
                        "Maintenance \"test_window\" has started\nScheduled DB upgrade"
                }
            }

            `when`("it gets an end event without a description") {
                val event = MaintenanceWindowEndEvent(window)

                then("it should return only the plain summary") {
                    formatter.toFormattedMessage(event) shouldBe
                        "Maintenance \"test_window\" has ended"
                }
            }

            `when`("it gets an end event with a description") {
                val event = MaintenanceWindowEndEvent(window.copy().apply { setDescription("Scheduled DB upgrade") })

                then("it should return only the plain summary, omitting the description") {
                    formatter.toFormattedMessage(event) shouldBe
                        "Maintenance \"test_window\" has ended"
                }
            }
        }
    }
)
