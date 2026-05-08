package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.WebhookMessageFactory
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.PlainTextMessageFormatter
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.kotest.assertions.fail
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import java.net.URI

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class GenericWebhookServiceTest(
    private val messageFactory: WebhookMessageFactory,
) : BehaviorSpec({

    val mockClient = mockk<GenericWebhookClient>()
    val webhookService = GenericWebhookService(mockClient, messageFactory)

    val webhookUrl = "https://irrelevant.com/webhook"

    afterTest { clearMocks(mockClient) }

    fun buildConfig(
        template: String? = null,
        excludedEventTypes: List<IntegrationEventType> = emptyList(),
        customHeaders: Map<String, String> = mapOf("X-Custom-Header" to "custom-value"),
    ) = mockk<WebhookNotificationConfig>(relaxed = true) {
        every { url } returns webhookUrl
        every { requestHeaders } returns customHeaders
        every { payloadTemplate } returns template
        every { excludedEvents } returns excludedEventTypes
    }

    val expectedEventDetails = webhookService.testEvents.map { event ->
        when (event) {
            is UptimeMonitorEvent -> PlainTextMessageFormatter.toFormattedMessage(event)
            is SSLMonitorEvent -> PlainTextMessageFormatter.toFormattedMessage(event)
            else -> fail("Unexpected event type: ${event::class}")
        }
    }

    given("sendTestMessage() - generic messages") {

        `when`("events are not excluded from the config") {
            val config = buildConfig()

            every { mockClient.sendGenericMessage(any(), any(), any()) } returns Single.just("OK")

            val result = webhookService.sendTestMessage(config).blockingGet()

            then("it should send a test message for every event type by default") {
                result.success shouldBe true
                result.message shouldBe Messages.successfulTestResultMessage()

                val genericMessages = mutableListOf<GenericWebhookMessage>()
                verify(exactly = 7) {
                    mockClient.sendGenericMessage(
                        webhookUrl = URI(webhookUrl),
                        message = capture(genericMessages),
                        headers = mapOf("X-Custom-Header" to "custom-value"),
                    )
                }
                genericMessages.map { it.type } shouldContainExactly listOf(
                    IntegrationEventType.HTTP_DOWN,
                    IntegrationEventType.HTTP_UP,
                    IntegrationEventType.SSL_WILL_EXPIRE,
                    IntegrationEventType.SSL_INVALID,
                    IntegrationEventType.SSL_VALID,
                    IntegrationEventType.PUSH_DOWN,
                    IntegrationEventType.PUSH_UP,
                )

                genericMessages.map { it.eventDetails } shouldContainExactly expectedEventDetails

                genericMessages.forAll { message ->
                    message.monitorName shouldBe "Test monitor"
                }
                genericMessages.forExactly(5) { message ->
                    message.monitorId shouldBe 1
                    message.monitorUrn shouldBe MonitorID(MonitorType.HTTP_SSL, "Test monitor").toString()
                }
                genericMessages.forExactly(2) { message ->
                    message.monitorId shouldBe 2
                    message.monitorUrn shouldBe MonitorID(MonitorType.PUSH, "Test monitor").toString()
                }
            }
        }

        `when`("events are excluded from the config") {
            val config = buildConfig(
                excludedEventTypes = listOf(IntegrationEventType.HTTP_UP, IntegrationEventType.PUSH_UP),
            )

            every { mockClient.sendGenericMessage(any(), any(), any()) } returns Single.just("OK")

            val result = webhookService.sendTestMessage(config).blockingGet()

            then("it should send a test message for every event type by default") {
                result.success shouldBe true
                result.message shouldBe Messages.successfulTestResultMessage()

                val genericMessages = mutableListOf<GenericWebhookMessage>()
                verify(exactly = 5) {
                    mockClient.sendGenericMessage(
                        webhookUrl = URI(webhookUrl),
                        message = capture(genericMessages),
                        headers = mapOf("X-Custom-Header" to "custom-value"),
                    )
                }
                genericMessages.map { it.type } shouldContainExactly listOf(
                    IntegrationEventType.HTTP_DOWN,
                    IntegrationEventType.SSL_WILL_EXPIRE,
                    IntegrationEventType.SSL_INVALID,
                    IntegrationEventType.SSL_VALID,
                    IntegrationEventType.PUSH_DOWN,
                )

                genericMessages.forAll { message ->
                    message.monitorName shouldBe "Test monitor"
                }
                genericMessages.forExactly(4) { message ->
                    message.monitorId shouldBe 1
                    message.monitorUrn shouldBe MonitorID(MonitorType.HTTP_SSL, "Test monitor").toString()
                }
                genericMessages.forExactly(1) { message ->
                    message.monitorId shouldBe 2
                    message.monitorUrn shouldBe MonitorID(MonitorType.PUSH, "Test monitor").toString()
                }
            }
        }

        `when`("templated headers are present") {
            val config = buildConfig(
                excludedEventTypes = IntegrationEventType.entries.minus(IntegrationEventType.PUSH_DOWN),
                customHeaders = mapOf(
                    "X-Custom-Header" to "custom-value",
                    "X-Event-Type" to "{{ ctx.type }}",
                    "ThisIsAlsoTemplated" to "{{ ctx.monitorName}}",
                )
            )

            every { mockClient.sendGenericMessage(any(), any(), any()) } returns Single.just("OK")

            val result = webhookService.sendTestMessage(config).blockingGet()

            then("it should compile them with the event") {
                result.success shouldBe true
                result.message shouldBe Messages.successfulTestResultMessage()

                verify(exactly = 1) {
                    mockClient.sendGenericMessage(
                        webhookUrl = URI(webhookUrl),
                        message = any(),
                        headers = mapOf(
                            "X-Custom-Header" to "custom-value",
                            "X-Event-Type" to "PUSH_DOWN",
                            "ThisIsAlsoTemplated" to "Test monitor",
                        ),
                    )
                }
            }
        }

        `when`("the client call fails") {
            val config = buildConfig()

            every {
                mockClient.sendGenericMessage(
                    any(),
                    any(),
                    any(),
                )
            } returns Single.error(HttpClientException("error"))

            val result = webhookService.sendTestMessage(config).blockingGet()

            then("it should return a failed result") {}
            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("error")
        }
    }

    given("sendTestMessage() - templated messages") {

        `when`("events are not excluded from the config") {
            val config = buildConfig(
                template = "Event type: {{ ctx.type }}",
            )

            every { mockClient.sendTemplatedMessage(any(), any(), any()) } returns Single.just("OK")

            val result = webhookService.sendTestMessage(config).blockingGet()

            then("it should send a test message for every event type by default") {
                result.success shouldBe true
                result.message shouldBe Messages.successfulTestResultMessage()

                val templatedMessages = mutableListOf<String>()
                verify(exactly = 7) {
                    mockClient.sendTemplatedMessage(
                        webhookUrl = URI(webhookUrl),
                        payload = capture(templatedMessages),
                        headers = mapOf("X-Custom-Header" to "custom-value"),
                    )
                }
                templatedMessages shouldContainExactly listOf(
                    "Event type: HTTP_DOWN",
                    "Event type: HTTP_UP",
                    "Event type: SSL_WILL_EXPIRE",
                    "Event type: SSL_INVALID",
                    "Event type: SSL_VALID",
                    "Event type: PUSH_DOWN",
                    "Event type: PUSH_UP",
                )
            }
        }

        `when`("events are excluded from the config") {
            val config = buildConfig(
                template = "Event type: {{ ctx.type }}",
                excludedEventTypes = listOf(IntegrationEventType.HTTP_UP, IntegrationEventType.PUSH_UP),
            )

            every { mockClient.sendTemplatedMessage(any(), any(), any()) } returns Single.just("OK")

            val result = webhookService.sendTestMessage(config).blockingGet()

            then("it should send a test message for every event type by default") {
                result.success shouldBe true
                result.message shouldBe Messages.successfulTestResultMessage()

                val templatedMessages = mutableListOf<String>()
                verify(exactly = 5) {
                    mockClient.sendTemplatedMessage(
                        webhookUrl = URI(webhookUrl),
                        payload = capture(templatedMessages),
                        headers = mapOf("X-Custom-Header" to "custom-value"),
                    )
                }
                templatedMessages shouldContainExactly listOf(
                    "Event type: HTTP_DOWN",
                    "Event type: SSL_WILL_EXPIRE",
                    "Event type: SSL_INVALID",
                    "Event type: SSL_VALID",
                    "Event type: PUSH_DOWN",
                )
            }
        }

        `when`("templated headers are present") {
            val config = buildConfig(
                excludedEventTypes = IntegrationEventType.entries.minus(IntegrationEventType.PUSH_DOWN),
                template = "Event type: {{ ctx.type }}",
                customHeaders = mapOf(
                    "X-Custom-Header" to "custom-value",
                    "X-Event-Type" to "{{ ctx.type }}",
                    "ThisIsAlsoTemplated" to "{{ ctx.monitorName}}",
                    "VerbatimShouldBeFineToo" to "{% verbatim %}{%{% endverbatim %}"
                )
            )

            every { mockClient.sendTemplatedMessage(any(), any(), any()) } returns Single.just("OK")

            val result = webhookService.sendTestMessage(config).blockingGet()

            then("it should compile them with the event") {
                result.success shouldBe true
                result.message shouldBe Messages.successfulTestResultMessage()

                verify(exactly = 1) {
                    mockClient.sendTemplatedMessage(
                        webhookUrl = URI(webhookUrl),
                        payload = "Event type: PUSH_DOWN",
                        headers = mapOf(
                            "X-Custom-Header" to "custom-value",
                            "X-Event-Type" to "PUSH_DOWN",
                            "ThisIsAlsoTemplated" to "Test monitor",
                            "VerbatimShouldBeFineToo" to "{%",
                        ),
                    )
                }
            }
        }

        `when`("the client call fails") {
            val config = buildConfig(
                template = "Event type: {{ ctx.type }}",
            )

            every {
                mockClient.sendTemplatedMessage(
                    any(),
                    any(),
                    any(),
                )
            } returns Single.error(HttpClientException("error"))

            val result = webhookService.sendTestMessage(config).blockingGet()

            then("it should return a failed result") {}
            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("error")
        }
    }
})
