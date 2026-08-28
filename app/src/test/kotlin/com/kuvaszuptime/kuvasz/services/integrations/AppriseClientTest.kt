package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.AppriseMessageFactory
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
class AppriseClientTest(
    private val client: AppriseClient,
    private val messageFactory: AppriseMessageFactory,
) : ShouldSpec({

    lateinit var mockServer: ClientAndServer
    lateinit var notifyUrl: java.net.URI

    // An ephemeral port keeps this spec out of the way of the other MockServer based ones
    beforeSpec {
        mockServer = ClientAndServer.startClientAndServer(0)
        notifyUrl = "http://localhost:${mockServer.localPort}/notify/kuvasz".toUri()
    }

    afterSpec { mockServer.stop() }

    afterTest { mockServer.reset() }

    val monitor = HttpMonitorRecord()
        .setId(1111)
        .setName("test_monitor")
        .setUrl("https://test.url")
        .setSensitiveUrl(false)

    val testMessage = messageFactory.fromUptimeEvent(HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null))

    context("the Apprise client") {

        @Suppress("MaxLineLength")
        should("send the notification as a JSON POST request") {
            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/notify/kuvasz")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(
                    JsonBody.json(
                        """{"title":"✅ Your monitor \"test_monitor\" (https://test.url) is UP (200)","body":"Latency: 300ms","type":"success","format":"text"}"""
                    )
                )

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code).withBody("""{"error":""}""")
            )

            client.sendMessage(notifyUrl, emptyMap(), testMessage).blockingGet() shouldBe """{"error":""}"""

            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("attach the configured request headers") {
            val request = request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/notify/kuvasz")
                .withHeader("Authorization", "Bearer apprise-token")
                .withHeader("X-Custom-Header", "custom-value")

            mockServer.`when`(request).respond(response().withStatusCode(HttpStatus.OK.code).withBody("OK"))

            val headers = mapOf(
                "Authorization" to "Bearer apprise-token",
                "X-Custom-Header" to "custom-value",
            )
            client.sendMessage(notifyUrl, headers, testMessage).blockingGet() shouldBe "OK"

            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        // A bodyless success must not blow up on the missing response body
        should("fall back to a synthetic body when the response has none") {
            val request = request().withMethod(HttpMethod.POST.name).withPath("/notify/kuvasz")

            mockServer.`when`(request).respond(response().withStatusCode(HttpStatus.NO_CONTENT.code))

            client.sendMessage(notifyUrl, emptyMap(), testMessage).blockingGet() shouldBe "OK"

            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("propagate the error when Apprise rejects the request") {
            val request = request().withMethod(HttpMethod.POST.name).withPath("/notify/kuvasz")

            mockServer.`when`(request).respond(response().withStatusCode(HttpStatus.BAD_REQUEST.code))

            client.sendMessage(notifyUrl, emptyMap(), testMessage).blockingSubscribe(
                { fail("Should not succeed") },
                { error -> error.message shouldBe "Bad Request" }
            )

            mockServer.verify(request, VerificationTimes.exactly(4))
        }
    }
})
