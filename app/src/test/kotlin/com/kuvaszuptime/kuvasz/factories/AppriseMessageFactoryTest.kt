package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.handlers.AppriseFormat
import com.kuvaszuptime.kuvasz.models.handlers.AppriseMessage
import com.kuvaszuptime.kuvasz.models.handlers.AppriseType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.http.HttpStatus
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import tools.jackson.module.kotlin.jacksonObjectMapper

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class AppriseMessageFactoryTest(
    private val factory: AppriseMessageFactory,
    private val jsonMapper: JsonMapper,
) : ShouldSpec({

    val treeMapper = jacksonObjectMapper()

    val monitor = HttpMonitorRecord()
        .setId(1111)
        .setName("test_monitor")
        .setUrl("https://test.url")
        .setSensitiveUrl(false)

    // The factory repeats the title in the body of an event without any detail, since Apprise needs a non-empty body
    fun AppriseMessage.detailLines(): List<String> = if (body == title) emptyList() else body.split("\n")

    context("the notification payload") {

        should("serialize into exactly the payload the Apprise API expects") {
            val message = factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null))

            val expected = """
                {
                  "title": "✅ Your monitor \"test_monitor\" (https://test.url) is UP (200)",
                  "body": "Latency: 300ms",
                  "type": "success",
                  "format": "text"
                }
            """.trimIndent()

            val actual = jsonMapper.writeValueAsString(message)

            treeMapper.readTree(actual) shouldBe treeMapper.readTree(expected)
        }

        should("leave the tag and the target URLs to the service, so they never leak into the factory") {
            val message = factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null))

            message.tag shouldBe null
            message.urls shouldBe null
            jsonMapper.writeValueAsString(message) shouldNotContain "null"
        }

        should("declare the payload as plain text, since it never carries any markup") {
            factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null)).format shouldBe
                AppriseFormat.TEXT
        }
    }

    context("the title and the body") {

        should("render the summary as the title and the rest of the message as the body") {
            val previousEvent = SslEventRecord().setStartedAt(getCurrentTimestamp().minusMinutes(30))
            val message = factory.fromSSLEvent(
                SSLInvalidEvent(monitor, SSLValidationError("Chain error"), previousEvent)
            )

            message.title shouldContain
                "Your site \"test_monitor\" (https://test.url) has an INVALID certificate"
            message.detailLines().size shouldBe 2
            message.detailLines().first() shouldContain "Chain error"
        }

        should("repeat the title in the body of an event without any detail, as Apprise rejects an empty body") {
            val message = factory.fromUptimeEvent(
                HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)
            )

            message.title shouldContain "Your monitor \"test_monitor\" (https://test.url) is DOWN (500)"
            message.body shouldBe message.title
        }

        should("keep the newlines of a multi-line detail as they are") {
            val message = factory.fromMaintenanceEvent(
                MaintenanceWindowStartEvent(
                    maintenanceWindow().setDescription("Rolling restart\nExpect a short downtime")
                )
            )

            message.body shouldBe "Rolling restart\nExpect a short downtime"
        }

        should("join the drifted DNS records into the body") {
            val dnsMonitor = DnsMonitorRecord().setId(2222).setName("drift_monitor")
            val message = factory.fromDnsRecordsChangedEvent(
                DnsRecordsChangedEvent(
                    monitor = dnsMonitor,
                    previousRecords = mapOf(
                        DnsRecordType.A to listOf("1.1.1.1"),
                        DnsRecordType.MX to listOf("mx1.test"),
                    ),
                    currentRecords = mapOf(
                        DnsRecordType.A to listOf("2.2.2.2"),
                        DnsRecordType.MX to listOf("mx2.test"),
                    ),
                )
            )

            message.body shouldBe "A: [1.1.1.1] → [2.2.2.2]\nMX: [mx1.test] → [mx2.test]"
        }
    }

    context("the details of the monitor events") {

        val icmpMonitor = IcmpMonitorRecord().setId(3333).setName("test_icmp_monitor").setHost("example.com")
        val tcpMonitor = TcpMonitorRecord().setId(4444).setName("test_tcp_monitor").setHost("example.com")
        val dnsMonitor = DnsMonitorRecord().setId(5555).setName("test_dns_monitor").setHost("example.com")
        val pushMonitor = PushMonitorRecord().setId(6666).setName("test_push_monitor")

        should("carry the latency and the packet loss of an ICMP event") {
            factory.fromUptimeEvent(IcmpMonitorUpEvent(icmpMonitor, null, 300, 0)).detailLines() shouldBe
                listOf("Avg. latency: 300 ms", "Packet loss: 0%")
            factory.fromUptimeEvent(IcmpMonitorDownEvent(icmpMonitor, "icmp error", null, 100)).detailLines() shouldBe
                listOf("Packet loss: 100%")
        }

        should("omit the latency of an event that has none") {
            factory.fromUptimeEvent(IcmpMonitorUpEvent(icmpMonitor, null, null, 0)).detailLines() shouldBe
                listOf("Packet loss: 0%")
            factory.fromUptimeEvent(TcpMonitorUpEvent(tcpMonitor, null, null)).detailLines() shouldBe emptyList()
            factory.fromUptimeEvent(DnsMonitorUpEvent(dnsMonitor, null, null)).detailLines() shouldBe emptyList()
        }

        should("carry the latency of the TCP and DNS events") {
            factory.fromUptimeEvent(TcpMonitorUpEvent(tcpMonitor, null, 300)).detailLines() shouldBe
                listOf("Connect latency: 300 ms")
            factory.fromUptimeEvent(DnsMonitorUpEvent(dnsMonitor, null, 300)).detailLines() shouldBe
                listOf("Resolution latency: 300 ms")
        }

        should("not surface the error of a down event, just like the other chat integrations") {
            factory.fromUptimeEvent(TcpMonitorDownEvent(tcpMonitor, "tcp error", null)).detailLines() shouldBe
                emptyList()
            factory.fromUptimeEvent(DnsMonitorDownEvent(dnsMonitor, "dns error", null)).detailLines() shouldBe
                emptyList()
            factory.fromUptimeEvent(PushMonitorDownEvent(pushMonitor, "push error", null)).detailLines() shouldBe
                emptyList()
        }

        should("carry the duration of the previous, ended event") {
            val previousEvent = TcpUptimeEventRecord()
                .setStatus(UptimeStatus.DOWN)
                .setStartedAt(getCurrentTimestamp().minusMinutes(30))
            val event = TcpMonitorUpEvent(tcpMonitor, previousEvent, 300)
            val duration = previousEvent.startedAt.diffToDuration(event.dispatchedAt).toDurationString()

            factory.fromUptimeEvent(event).detailLines() shouldBe
                listOf("Connect latency: 300 ms", "Was down for $duration")
        }

        should("render a push event that has nothing but its summary") {
            val message = factory.fromUptimeEvent(PushMonitorUpEvent(pushMonitor, null))

            message.title shouldBe "✅ Your monitor \"test_push_monitor\" is UP"
            message.detailLines() shouldBe emptyList()
        }
    }

    context("the type of the notification") {

        should("be SUCCESS for a recovery") {
            factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null)).type shouldBe
                AppriseType.SUCCESS
            factory.fromSSLEvent(SSLValidEvent(monitor, generateCertificateInfo(), null)).type shouldBe
                AppriseType.SUCCESS
            factory.fromMaintenanceEvent(MaintenanceWindowEndEvent(maintenanceWindow())).type shouldBe
                AppriseType.SUCCESS
        }

        should("be FAILURE for an outage") {
            factory.fromUptimeEvent(
                HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)
            ).type shouldBe AppriseType.FAILURE
            factory.fromSSLEvent(SSLInvalidEvent(monitor, SSLValidationError("Chain error"), null)).type shouldBe
                AppriseType.FAILURE
        }

        should("be WARNING for an upcoming SSL expiry") {
            factory.fromSSLEvent(SSLWillExpireEvent(monitor, generateCertificateInfo(), null)).type shouldBe
                AppriseType.WARNING
        }

        should("be INFO for the informational events") {
            factory.fromDnsRecordsChangedEvent(
                DnsRecordsChangedEvent(
                    monitor = DnsMonitorRecord().setId(2222).setName("drift_monitor"),
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )
            ).type shouldBe AppriseType.INFO
            factory.fromMaintenanceEvent(MaintenanceWindowStartEvent(maintenanceWindow())).type shouldBe
                AppriseType.INFO
        }
    }

    context("testMessage") {

        should("render the localized test message as an informational notification") {
            val message = factory.testMessage()

            message.type shouldBe AppriseType.INFO
            message.title shouldBe Messages.integrationTestMessage()
            message.detailLines() shouldBe emptyList()
        }
    }
})

private fun maintenanceWindow() = MaintenanceWindowRecord()
    .setId(1)
    .setName("Planned upgrade")
    .setDescription("Rolling restart")
