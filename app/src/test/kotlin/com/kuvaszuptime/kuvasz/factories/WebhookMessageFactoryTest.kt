package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class WebhookMessageFactoryTest(private val factory: WebhookMessageFactory) : ShouldSpec({

    context("fromMonitorEvent with custom template") {

        should("render the correct output if template is valid") {
            @Suppress("MaxLineLength")
            val template =
                """{"id": "{{ctx.monitorUrn}}","status": {% if ctx.type == 'HTTP_UP' %}"OK"{% else %}"{{ctx.type}}"{% endif %}}"""

            val resultFromUpEvent = factory.fromEvent(
                event = HttpMonitorUpEvent(
                    monitor = HttpMonitorRecord().apply {
                        id = 23423
                        name = "something"
                        sensitiveUrl = false
                        url = "https://irrelevant"
                    },
                    status = HttpStatus.OK,
                    latency = 123,
                    previousEvent = null,
                ),
                literalTemplate = template,
            )
            val resultFromDownMonitor = factory.fromEvent(
                event = HttpMonitorDownEvent(
                    monitor = HttpMonitorRecord().apply {
                        id = 23423
                        name = "something"
                        sensitiveUrl = false
                        url = "https://irrelevant"
                    },
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = null,
                ),
                literalTemplate = template,
            )

            resultFromUpEvent shouldBe """{"id": "http:something","status": "OK"}"""
            resultFromDownMonitor shouldBe """{"id": "http:something","status": "HTTP_DOWN"}"""
        }

        should("escape special characters according to the provided strategy") {
            @Suppress("MaxLineLength")
            val jsEscapedTemplate =
                """{{ ctx.eventDetails | escape(strategy="js") }}"""
            val defaultEscapedTemplate =
                """{{ ctx.eventDetails }}"""
            val testEvent = HttpMonitorUpEvent(
                monitor = HttpMonitorRecord().apply {
                    id = 23423
                    name = "something"
                    sensitiveUrl = false
                    url = "https://irrelevant"
                },
                status = HttpStatus.OK,
                latency = 123,
                previousEvent = null,
            )

            val defaultResult = factory.fromEvent(testEvent, defaultEscapedTemplate)
            val jsEscapedResult = factory.fromEvent(testEvent, jsEscapedTemplate)

            defaultResult shouldContain "Your monitor &quot;something&quot;"
            jsEscapedResult shouldContain """Your monitor \"something\""""
        }
    }

    context("fromEvent with the events that are not uptime or SSL changes") {

        should("render a DNS drift event with the DNS monitor's identity and the changed records") {
            val message = factory.fromEvent(
                DnsRecordsChangedEvent(
                    monitor = DnsMonitorRecord().setId(5555).setName("my-dns"),
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )
            )

            message.type shouldBe IntegrationEventType.DNS_RECORDS_CHANGED
            message.monitorId shouldBe 5555
            message.monitorUrn shouldBe "dns:my-dns"
            message.monitorName shouldBe "my-dns"
            message.monitorDetailsUrl shouldBe "/dns-monitors/5555"
            message.eventDetails shouldBe "DNS records changed for monitor \"my-dns\"\nA: [1.1.1.1] → [2.2.2.2]"
        }

        should("render a container restart event with the Docker monitor's identity and the restart counts") {
            val message = factory.fromEvent(
                DockerContainerRestartedEvent(
                    monitor = DockerMonitorRecord().setId(7777).setName("my-app"),
                    previousRestartCount = 2,
                    currentRestartCount = 5,
                )
            )

            message.type shouldBe IntegrationEventType.DOCKER_CONTAINER_RESTARTED
            message.monitorId shouldBe 7777
            message.monitorUrn shouldBe "docker:my-app"
            message.monitorName shouldBe "my-app"
            message.monitorDetailsUrl shouldBe "/docker-monitors/7777"
            message.eventDetails shouldBe
                "The container of monitor \"my-app\" has been restarted\nRestarts since the last check: 3 (total: 5)"
        }
    }

    should("throw if template is invalid") {
        val template = """{"request_id": "342342","status": {% if ctx.type == 'UP' endif %}}"""

        shouldThrowAny {
            factory.fromEvent(
                event = HttpMonitorUpEvent(
                    monitor = HttpMonitorRecord().apply {
                        id = 23423
                        name = "something"
                        sensitiveUrl = false
                        url = "https://irrelevant"
                    },
                    status = HttpStatus.OK,
                    latency = 123,
                    previousEvent = null,
                ),
                literalTemplate = template,
            )
        }
    }

    should("throw if template references a non-existent object") {
        @Suppress("MaxLineLength")
        val template = """{"status": {% if context.type == 'HTTP_UP' %}"OK"{% else %}"{{ctx.type}}"{% endif %}}"""

        shouldThrowAny {
            factory.fromEvent(
                event = HttpMonitorUpEvent(
                    monitor = HttpMonitorRecord().apply {
                        id = 23423
                        name = "something"
                        sensitiveUrl = false
                        url = "https://irrelevant"
                    },
                    status = HttpStatus.OK,
                    latency = 123,
                    previousEvent = null,
                ),
                literalTemplate = template,
            )
        }
    }
})
