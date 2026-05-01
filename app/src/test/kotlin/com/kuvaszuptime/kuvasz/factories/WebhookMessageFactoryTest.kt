package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class WebhookMessageFactoryTest(private val factory: WebhookMessageFactory) : ShouldSpec({

    context("fromMonitorEvent with custom template") {

        should("render the correct output if template is valid") {
            @Suppress("MaxLineLength")
            val template =
                """{"request_id": "342342","status": {% if ctx.type == 'HTTP_UP' %}"OK"{% else %}"{{ctx.type}}"{% endif %}}"""

            val resultFromUpEvent = factory.fromMonitorEvent(
                event = HttpMonitorUpEvent(
                    monitor = HttpMonitorRecord().apply {
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
            val resultFromDownMonitor = factory.fromMonitorEvent(
                event = HttpMonitorDownEvent(
                    monitor = HttpMonitorRecord().apply {
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

            resultFromUpEvent shouldBe """{"request_id": "342342","status": "OK"}"""
            resultFromDownMonitor shouldBe """{"request_id": "342342","status": "HTTP_DOWN"}"""
        }
    }
})
