package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.AdaptiveCard
import com.kuvaszuptime.kuvasz.models.handlers.CardContainer
import com.kuvaszuptime.kuvasz.models.handlers.CardTextBlock
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsAttachment
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsMessage
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.http.HttpStatus
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import tools.jackson.module.kotlin.jacksonObjectMapper

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class MsTeamsCardFactoryTest(
    private val factory: MsTeamsCardFactory,
    private val jsonMapper: JsonMapper,
) : ShouldSpec({

    val treeMapper = jacksonObjectMapper()

    val monitor = HttpMonitorRecord()
        .setId(1111)
        .setName("test_monitor")
        .setUrl("https://test.url")
        .setSensitiveUrl(false)

    fun MsTeamsMessage.card(): AdaptiveCard = attachments.single().content

    fun MsTeamsMessage.container(): CardContainer = card().body.first().shouldBeInstanceOf<CardContainer>()

    fun MsTeamsMessage.title(): CardTextBlock = container().items.single().shouldBeInstanceOf<CardTextBlock>()

    fun MsTeamsMessage.details(): List<CardTextBlock> =
        card().body.drop(1).map { it.shouldBeInstanceOf<CardTextBlock>() }

    context("the Adaptive Card envelope") {

        should("serialize into exactly the payload the Teams Workflows trigger expects") {
            val message = factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null))

            val expected = """
                {
                  "type": "message",
                  "attachments": [
                    {
                      "contentType": "application/vnd.microsoft.card.adaptive",
                      "content": {
                        "${'$'}schema": "http://adaptivecards.io/schemas/adaptive-card.json",
                        "type": "AdaptiveCard",
                        "version": "1.4",
                        "msteams": { "width": "Full" },
                        "body": [
                          {
                            "type": "Container",
                            "style": "good",
                            "bleed": true,
                            "items": [
                              {
                                "type": "TextBlock",
                                "text": "✅ **Your monitor \"test_monitor\" (https://test.url) is UP (200)**",
                                "wrap": true,
                                "size": "Medium"
                              }
                            ]
                          },
                          {
                            "type": "TextBlock",
                            "text": "_Latency: 300ms_",
                            "wrap": true,
                            "isSubtle": true,
                            "spacing": "Small"
                          }
                        ]
                      }
                    }
                  ]
                }
            """.trimIndent()

            val actual = jsonMapper.writeValueAsString(message)

            treeMapper.readTree(actual) shouldBe treeMapper.readTree(expected)
        }

        should("never emit null fields, which the Teams card renderer rejects") {
            val message = factory.fromUptimeEvent(
                HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)
            )

            jsonMapper.writeValueAsString(message) shouldNotContain "null"
        }

        should("use the constants of the card model") {
            val message = factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null))

            message.type shouldBe MsTeamsMessage.MESSAGE_TYPE
            message.attachments.single().contentType shouldBe MsTeamsAttachment.ADAPTIVE_CARD_CONTENT_TYPE
            message.card().type shouldBe AdaptiveCard.CARD_TYPE
            message.card().schema shouldBe AdaptiveCard.SCHEMA_URL
            message.card().version shouldBe AdaptiveCard.SCHEMA_VERSION
        }
    }

    context("the card body") {

        should("render the first message line as the title and the rest as subtle detail blocks") {
            val previousEvent = SslEventRecord().setStartedAt(getCurrentTimestamp().minusMinutes(30))
            val message = factory.fromSSLEvent(
                SSLInvalidEvent(monitor, SSLValidationError("Chain error"), previousEvent)
            )

            message.title().text shouldContain
                "Your site \"test_monitor\" (https://test.url) has an INVALID certificate"
            message.title().size shouldBe "Medium"
            message.title().isSubtle shouldBe null

            message.details().size shouldBe 2
            message.details().forEach { detail ->
                detail.isSubtle shouldBe true
                detail.spacing shouldBe "Small"
                detail.size shouldBe null
            }
            message.details().first().text shouldContain "Chain error"
        }

        should("render a single-line message without any detail block") {
            val message = factory.fromUptimeEvent(
                HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)
            )

            message.details() shouldBe emptyList()
            message.title().text shouldContain "Your monitor \"test_monitor\" (https://test.url) is DOWN (500)"
        }

        should("double the newlines inside a detail block, since Teams ignores single ones") {
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

            message.details().single().text shouldBe "A: [1.1.1.1] → [2.2.2.2]\n\nMX: [mx1.test] → [mx2.test]"
        }
    }

    context("the severity of the container") {

        should("be 'good' for a recovery") {
            factory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null))
                .container().style shouldBe "good"
            factory.fromSSLEvent(SSLValidEvent(monitor, generateCertificateInfo(), null))
                .container().style shouldBe "good"
            factory.fromMaintenanceEvent(MaintenanceWindowEndEvent(maintenanceWindow()))
                .container().style shouldBe "good"
        }

        should("be 'attention' for an outage") {
            factory.fromUptimeEvent(
                HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)
            ).container().style shouldBe "attention"
            factory.fromSSLEvent(SSLInvalidEvent(monitor, SSLValidationError("Chain error"), null))
                .container().style shouldBe "attention"
        }

        should("be 'warning' for an upcoming SSL expiry") {
            factory.fromSSLEvent(SSLWillExpireEvent(monitor, generateCertificateInfo(), null))
                .container().style shouldBe "warning"
        }

        should("be 'accent' for the informational events") {
            factory.fromDnsRecordsChangedEvent(
                DnsRecordsChangedEvent(
                    monitor = DnsMonitorRecord().setId(2222).setName("drift_monitor"),
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )
            ).container().style shouldBe "accent"
            factory.fromMaintenanceEvent(MaintenanceWindowStartEvent(maintenanceWindow()))
                .container().style shouldBe "accent"
        }
    }

    context("testMessage") {

        should("render the localized test message as an informational card") {
            val message = factory.testMessage()

            message.container().style shouldBe "accent"
            message.title().text shouldBe Messages.integrationTestMessage()
            message.details() shouldBe emptyList()
        }
    }
})

private fun maintenanceWindow() = MaintenanceWindowRecord()
    .setId(1)
    .setName("Planned upgrade")
    .setDescription("Rolling restart")
