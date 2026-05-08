package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
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
class GenericWebhookClientTest(
    private val client: GenericWebhookClient,
) : ShouldSpec({

    lateinit var mockServer: ClientAndServer
    val mockServerUrl = "http://localhost:1080"
    val webhookUrl = "$mockServerUrl/webhook".toUri()

    beforeSpec {
        mockServer = ClientAndServer.startClientAndServer(1080)
    }

    afterSpec {
        mockServer.stop()
    }

    afterTest { mockServer.reset() }

    val testMessage = GenericWebhookMessage(
        monitorId = 123,
        monitorUrn = MonitorID(MonitorType.HTTP_SSL, "test").toString(),
        monitorName = "test",
        monitorDetailsUrl = "/http-monitors/123",
        timestamp = 1278432,
        type = IntegrationEventType.HTTP_DOWN,
        eventDetails = "nice details!"
    )

    context("the webhook client") {

        @Suppress("MaxLineLength")
        should("should send a correct request to the provided target - generic message") {

            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(
                    JsonBody.json(
                        """{"monitorId":123,"monitorUrn":"http:test","monitorName":"test","monitorDetailsUrl":"/http-monitors/123","timestamp":1278432,"type":"HTTP_DOWN","eventDetails":"nice details!"}"""
                    )
                )

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.ACCEPTED.code)
                    .withBody("OK")
            )

            val response = client.sendGenericMessage(webhookUrl, testMessage, emptyMap()).blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        @Suppress("MaxLineLength")
        should("return a failed result when the target responds with an error - generic message") {
            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(
                    JsonBody.json(
                        """{"monitorId":123,"monitorUrn":"http:test","monitorName":"test","timestamp":1278432,"type":"HTTP_DOWN","eventDetails":"nice details!"}"""
                    )
                )
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.UNAUTHORIZED.code)
            )

            client.sendGenericMessage(webhookUrl, testMessage, emptyMap()).blockingSubscribe(
                { fail("Should not succeed") },
                { error -> error.message shouldBe "Unauthorized" }
            )
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("should send a correct request to the provided target - templated message") {

            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(JsonBody.json("""{"monitorId":123}"""))

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.ACCEPTED.code)
                    .withBody("OK")
            )

            val response = client.sendTemplatedMessage(webhookUrl, """{"monitorId":123}""", emptyMap()).blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("return a failed result when the target responds with an error - templated message") {
            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(JsonBody.json("""{"monitorId":123}"""))

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.UNAUTHORIZED.code)
            )

            client.sendTemplatedMessage(webhookUrl, """{"monitorId":123}""", emptyMap())
                .blockingSubscribe(
                    { fail("Should not succeed") },
                    { error -> error.message shouldBe "Unauthorized" }
                )
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("follow redirects") {
            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/webhook")
            val request2 = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/webhook-redirect")

            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.TEMPORARY_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "/webhook-redirect")
            )
            mockServer.`when`(request2).respond(
                response().withStatusCode(HttpStatus.OK.code)
                    .withBody("OK")
            )

            val response = client.sendGenericMessage(webhookUrl, testMessage, emptyMap()).blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
            mockServer.verify(request2, VerificationTimes.exactly(1))
        }

        should("be able overwrite the default Content-Type header and add custom ones") {
            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML)
                .withHeader("X-Custom-Header", "CustomValue")

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("OK")
            )

            val response = client.sendTemplatedMessage(
                webhookUrl = webhookUrl,
                payload = """<xml><monitorId>123</monitorId></xml>""",
                headers = mapOf(
                    "X-Custom-Header" to "CustomValue",
                    HttpHeaders.CONTENT_TYPE to MediaType.TEXT_XML,
                ),
            ).blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }
    }
})
