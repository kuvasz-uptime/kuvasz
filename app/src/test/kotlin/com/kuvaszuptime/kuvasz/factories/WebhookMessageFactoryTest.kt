package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import io.kotest.core.spec.style.StringSpec
import io.micronaut.http.HttpStatus

class WebhookMessageFactoryTest : StringSpec({

    val factory = WebhookMessageFactory()

    "fromUptimeEvent with custom template" {
        val template = """ {
            "request_id": "342342",
            "status": {% if event.uptimeStatus == 'UP' %}OK{% else %}{{event.uptimeStatus}}{% endif %}
            }
        """.trimIndent()

        val result = factory.fromUptimeEvent(
            event = HttpMonitorUpEvent(
                monitor = HttpMonitorRecord().apply { name = "something" },
                status = HttpStatus.OK,
                latency = 123,
                previousEvent = null,
            ),
            literalTemplate = template,
        )
        val result2 = factory.fromUptimeEvent(
            event = HttpMonitorDownEvent(
                monitor = HttpMonitorRecord().apply { name = "something" },
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                error = Exception(),
                previousEvent = null,
            ),
            literalTemplate = template,
        )

        println(result)
        println(result2)
    }
})
