package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.MsTeamsCardFactory
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.assertions.fail
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody
import org.mockserver.verify.VerificationTimes

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class MsTeamsWebhookClientTest(
    private val client: MsTeamsWebhookClient,
    private val cardFactory: MsTeamsCardFactory,
) : ShouldSpec({

    lateinit var mockServer: ClientAndServer
    val mockServerUrl = "http://localhost:1081"
    val webhookUrl = "$mockServerUrl/workflows/aaa/triggers/manual/paths/invoke".toUri()

    beforeSpec { mockServer = ClientAndServer.startClientAndServer(1081) }

    afterSpec { mockServer.stop() }

    afterTest { mockServer.reset() }

    val monitor = HttpMonitorRecord()
        .setId(1111)
        .setName("test_monitor")
        .setUrl("https://test.url")
        .setSensitiveUrl(false)

    val testMessage = cardFactory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null))

    context("the Microsoft Teams webhook client") {

        // The Workflows trigger rejects anything that isn't declared as JSON with a 400
        // ("InvalidRequestContent"), so the content type is as important as the body itself.
        @Suppress("MaxLineLength")
        should("send the Adaptive Card as a JSON POST request") {
            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/workflows/aaa/triggers/manual/paths/invoke")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(
                    JsonBody.json(
                        """{"type":"message","attachments":[{"contentType":"application/vnd.microsoft.card.adaptive","content":{"${'$'}schema":"http://adaptivecards.io/schemas/adaptive-card.json","type":"AdaptiveCard","version":"1.4","msteams":{"width":"Full"},"body":[{"type":"Container","style":"good","bleed":true,"items":[{"type":"TextBlock","text":"✅ Your monitor \"test_monitor\" (https://test.url) is UP (200)","wrap":true,"size":"Medium","weight":"Bolder"}]},{"type":"TextBlock","text":"Latency: 300ms","wrap":true,"isSubtle":true,"spacing":"Small"}]}}]}"""
                    )
                )

            // The trigger answers with an empty 202
            mockServer.`when`(request).respond(response().withStatusCode(HttpStatus.ACCEPTED.code))

            client.sendMessage(webhookUrl, testMessage).blockingGet() shouldBe "OK"

            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("propagate the error when the workflow rejects the request") {
            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/workflows/aaa/triggers/manual/paths/invoke")

            mockServer.`when`(request).respond(response().withStatusCode(HttpStatus.BAD_REQUEST.code))

            client.sendMessage(webhookUrl, testMessage).blockingSubscribe(
                { fail("Should not succeed") },
                { error -> error.message shouldBe "Bad Request" }
            )

            mockServer.verify(request, VerificationTimes.exactly(4))
        }
    }
})
