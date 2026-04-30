package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.micronaut.http.HttpStatus

// TODO restructure the tests
class WebhookMessageFactoryTest : StringSpec({

    val factory = WebhookMessageFactory()

    "fromMonitorEvent with custom template" {
        val template = """ {
            "request_id": "342342",
            "status": {% if ctx.type == 'HTTP_UP' %}OK{% else %}{{ctx.type}}{% endif %}
            }
        """.trimIndent()

        val result = factory.fromMonitorEvent(
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
        val result2 = factory.fromMonitorEvent(
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

        println(result)
        println(result2)
    }

    "fromMonitorEvent with invalid template" {
        val template = """ {
            "request_id": "342342",
            "status": {% if ctx.type == 'UP' endif %}
            }
        """.trimIndent()

        shouldThrowAny {
            factory.fromMonitorEvent(
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
        }
        shouldThrowAny {
            factory.fromMonitorEvent(
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
        }
    }
})
