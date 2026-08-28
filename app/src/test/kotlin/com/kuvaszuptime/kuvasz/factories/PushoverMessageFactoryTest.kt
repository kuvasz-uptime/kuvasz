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
import com.kuvaszuptime.kuvasz.models.handlers.PushoverMessage
import com.kuvaszuptime.kuvasz.models.handlers.PushoverPriority
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.http.HttpStatus
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import tools.jackson.module.kotlin.jacksonObjectMapper

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class PushoverMessageFactoryTest(
    private val factory: PushoverMessageFactory,
    private val jsonMapper: JsonMapper,
) : ShouldSpec({

    val treeMapper = jacksonObjectMapper()

    val monitor = HttpMonitorRecord()
        .setId(1111)
        .setName("test_monitor")
        .setUrl("https://test.url")
        .setSensitiveUrl(false)

    // The body always leads with the summary of the event, and the details follow it line by line
    fun PushoverMessage.summaryLine(): String = message.substringBefore("\n")

    fun PushoverMessage.detailLines(): List<String> = message.split("\n").drop(1)

    context("the notification payload") {

        should("serialize into exactly the payload the Pushover API expects") {
            val message = factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null))

            val expected = """
                {
                  "title": "✅ test_monitor",
                  "message": "Your monitor \"test_monitor\" (https://test.url) is UP (200)\nLatency: 300ms",
                  "priority": 0
                }
            """.trimIndent()

            val actual = jsonMapper.writeValueAsString(message)

            treeMapper.readTree(actual) shouldBe treeMapper.readTree(expected)
        }

        should("leave the credentials and the emergency parameters to the service") {
            val message = factory.fromUptimeEvent(
                HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)
            )

            message.token shouldBe null
            message.user shouldBe null
            message.device shouldBe null
            message.sound shouldBe null
            message.retry shouldBe null
            message.expire shouldBe null
            message.tags shouldBe null
            jsonMapper.writeValueAsString(message) shouldNotContain "null"
        }
    }

    context("the title and the body") {

        should("name the subject of the event in the title, and lead the body with its summary") {
            val previousEvent = SslEventRecord().setStartedAt(getCurrentTimestamp().minusMinutes(30))
            val message = factory.fromSSLEvent(
                SSLInvalidEvent(monitor, SSLValidationError("Chain error"), previousEvent)
            )

            message.title shouldBe "🚨 test_monitor"
            message.summaryLine() shouldContain
                "Your site \"test_monitor\" (https://test.url) has an INVALID certificate"
            message.detailLines().size shouldBe 2
            message.detailLines().first() shouldContain "Chain error"
        }

        should("carry nothing but the summary in the body of an event without any detail") {
            val message = factory.fromUptimeEvent(
                HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)
            )

            message.title shouldBe "🚨 test_monitor"
            message.message shouldContain "Your monitor \"test_monitor\" (https://test.url) is DOWN (500)"
            message.detailLines() shouldBe emptyList()
        }

        should("name the maintenance window in the title of a maintenance event") {
            factory.fromMaintenanceEvent(MaintenanceWindowStartEvent(maintenanceWindow())).title shouldBe
                "🔧 Planned upgrade"
            factory.fromMaintenanceEvent(MaintenanceWindowEndEvent(maintenanceWindow())).title shouldBe
                "✅ Planned upgrade"
        }

        should("keep the newlines of a multi-line detail as they are") {
            val message = factory.fromMaintenanceEvent(
                MaintenanceWindowStartEvent(
                    maintenanceWindow().setDescription("Rolling restart\nExpect a short downtime")
                )
            )

            message.detailLines() shouldBe listOf("Rolling restart", "Expect a short downtime")
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

            message.title shouldBe "ℹ️ drift_monitor"
            message.detailLines() shouldBe listOf("A: [1.1.1.1] → [2.2.2.2]", "MX: [mx1.test] → [mx2.test]")
        }
    }

    context("the limits of the Pushover API") {

        should("truncate a title that is longer than the limit") {
            val longName = "a".repeat(300)
            val message = factory.fromUptimeEvent(
                HttpMonitorUpEvent(
                    HttpMonitorRecord().setId(1).setName(longName).setUrl("https://test.url").setSensitiveUrl(false),
                    HttpStatus.OK,
                    300,
                    null,
                )
            )

            message.title.length shouldBe 250
            message.title shouldEndWith "…"
        }

        should("truncate a body that is longer than the limit") {
            val longDescription = "b".repeat(1200)
            val message = factory.fromMaintenanceEvent(
                MaintenanceWindowStartEvent(maintenanceWindow().setDescription(longDescription))
            )

            message.message.length shouldBe 1024
            message.message shouldEndWith "…"
        }

        should("leave a title and a body that fit as they are") {
            val message = factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null))

            message.title shouldNotContain "…"
            message.message shouldNotContain "…"
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

            message.title shouldBe "✅ test_push_monitor"
            message.message shouldBe "Your monitor \"test_push_monitor\" is UP"
            message.detailLines() shouldBe emptyList()
        }
    }

    context("the priority of the notification") {

        should("be HIGH for an outage, so that it breaks through the quiet hours") {
            factory.fromUptimeEvent(
                HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)
            ).priority shouldBe PushoverPriority.HIGH
            factory.fromSSLEvent(SSLInvalidEvent(monitor, SSLValidationError("Chain error"), null)).priority shouldBe
                PushoverPriority.HIGH
        }

        should("be NORMAL for a recovery") {
            factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null)).priority shouldBe
                PushoverPriority.NORMAL
            factory.fromSSLEvent(SSLValidEvent(monitor, generateCertificateInfo(), null)).priority shouldBe
                PushoverPriority.NORMAL
            factory.fromMaintenanceEvent(MaintenanceWindowEndEvent(maintenanceWindow())).priority shouldBe
                PushoverPriority.NORMAL
        }

        should("be NORMAL for the warnings and the informational events") {
            factory.fromSSLEvent(SSLWillExpireEvent(monitor, generateCertificateInfo(), null)).priority shouldBe
                PushoverPriority.NORMAL
            factory.fromDnsRecordsChangedEvent(
                DnsRecordsChangedEvent(
                    monitor = DnsMonitorRecord().setId(2222).setName("drift_monitor"),
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )
            ).priority shouldBe PushoverPriority.NORMAL
            factory.fromMaintenanceEvent(MaintenanceWindowStartEvent(maintenanceWindow())).priority shouldBe
                PushoverPriority.NORMAL
        }

        // The escalation to the emergency priority depends on the integration's configuration, so it's the
        // service's business, not the factory's
        should("never be EMERGENCY, whatever the event is") {
            factory.fromUptimeEvent(
                HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)
            ).priority shouldBe PushoverPriority.HIGH
            factory.testMessage().priority shouldBe PushoverPriority.NORMAL
        }
    }

    context("testMessage") {

        should("render the localized test message as a normal priority notification") {
            val message = factory.testMessage()

            message.priority shouldBe PushoverPriority.NORMAL
            message.title shouldBe "ℹ️ Kuvasz Uptime"
            message.message shouldBe Messages.integrationTestMessage()
            message.detailLines() shouldBe emptyList()
        }
    }
})

private fun maintenanceWindow() = MaintenanceWindowRecord()
    .setId(1)
    .setName("Planned upgrade")
    .setDescription("Rolling restart")
