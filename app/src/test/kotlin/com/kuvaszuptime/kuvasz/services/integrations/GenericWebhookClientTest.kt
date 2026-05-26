package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.WebhookHttpMethod
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
        should("send a correct POST request - generic message") {

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

            val response = client.sendMessage(WebhookHttpMethod.POST, webhookUrl, emptyMap(), testMessage).blockingGet()

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

            client.sendMessage(WebhookHttpMethod.POST, webhookUrl, emptyMap(), testMessage).blockingSubscribe(
                { fail("Should not succeed") },
                { error -> error.message shouldBe "Unauthorized" }
            )
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("send a correct POST request - templated message") {

            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(JsonBody.json("""{"monitorId":123}"""))

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.ACCEPTED.code)
                    .withBody("OK")
            )

            val response = client
                .sendMessage(WebhookHttpMethod.POST, webhookUrl, emptyMap(), """{"monitorId":123}""")
                .blockingGet()

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

            client.sendMessage(WebhookHttpMethod.POST, webhookUrl, emptyMap(), """{"monitorId":123}""")
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

            val response = client.sendMessage(WebhookHttpMethod.POST, webhookUrl, emptyMap(), testMessage).blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
            mockServer.verify(request2, VerificationTimes.exactly(1))
        }

        should("be able to overwrite the default Content-Type header and add custom ones") {
            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML)
                .withHeader("X-Custom-Header", "CustomValue")

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("OK")
            )

            val response = client.sendMessage(
                httpMethod = WebhookHttpMethod.POST,
                url = webhookUrl,
                headers = mapOf(
                    "X-Custom-Header" to "CustomValue",
                    HttpHeaders.CONTENT_TYPE to MediaType.TEXT_XML,
                ),
                payload = """<xml><monitorId>123</monitorId></xml>""",
            ).blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("send a PUT request when http-method is PUT - generic message") {
            val request = request()
                .withMethod(HttpMethod.PUT.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("OK")
            )

            val response = client.sendMessage(WebhookHttpMethod.PUT, webhookUrl, emptyMap(), testMessage).blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("send a PATCH request when http-method is PATCH - generic message") {
            val request = request()
                .withMethod(HttpMethod.PATCH.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("OK")
            )

            val response =
                client.sendMessage(WebhookHttpMethod.PATCH, webhookUrl, emptyMap(), testMessage).blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("send a PUT request when http-method is PUT - templated message") {
            val request = request()
                .withMethod(HttpMethod.PUT.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(JsonBody.json("""{"monitorId":123}"""))

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("OK")
            )

            val response =
                client.sendMessage(WebhookHttpMethod.PUT, webhookUrl, emptyMap(), """{"monitorId":123}""").blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("send a PATCH request when http-method is PATCH - templated message") {
            val request = request()
                .withMethod(HttpMethod.PATCH.name)
                .withPath("/webhook")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(JsonBody.json("""{"monitorId":123}"""))

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("OK")
            )

            val response = client.sendMessage(WebhookHttpMethod.PATCH, webhookUrl, emptyMap(), """{"monitorId":123}""")
                .blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("send a GET request when http-method is GET - generic message") {
            val request = request()
                .withMethod(HttpMethod.GET.name)
                .withPath("/webhook")

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("OK")
            )

            val response = client.sendMessage(WebhookHttpMethod.GET, webhookUrl, emptyMap(), testMessage).blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("send a GET request when http-method is GET - templated message") {
            val request = request()
                .withMethod(HttpMethod.GET.name)
                .withPath("/webhook")

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("OK")
            )

            val response =
                client.sendMessage(WebhookHttpMethod.GET, webhookUrl, emptyMap(), """{"monitorId":123}""").blockingGet()

            response shouldBe "OK"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("not send a Content-Type header when http-method is GET") {
            val requestMatcher = request()
                .withMethod(HttpMethod.GET.name)
                .withPath("/webhook")

            mockServer.`when`(requestMatcher).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("OK")
            )

            val response = client.sendMessage(WebhookHttpMethod.GET, webhookUrl, emptyMap(), testMessage).blockingGet()

            response shouldBe "OK"
            val recordedRequests = mockServer.retrieveRecordedRequests(requestMatcher)
            recordedRequests.size shouldBe 1
            recordedRequests[0].containsHeader(HttpHeaders.CONTENT_TYPE) shouldBe false
        }
    }
})
