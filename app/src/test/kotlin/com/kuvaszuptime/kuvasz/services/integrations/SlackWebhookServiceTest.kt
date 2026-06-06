package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackWebhookMessage
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class SlackWebhookServiceTest(
    private val client: SlackWebhookClient,
    private val slackWebhookService: SlackWebhookService,
) : ShouldSpec({

    context("sendTestMessage") {

        should("call the webhook client to send the message") {
            val testWebhookUrl = "https://an-url.com"
            val expectedMessage = SlackWebhookMessage(text = Messages.integrationTestMessage())
            val config = mockk<SlackNotificationConfig>(relaxed = true) {
                every { webhookUrl } returns testWebhookUrl
            }
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) } returns Single.just("ok")

            val result = slackWebhookService.sendTestMessage(config).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()

            verify(exactly = 1) { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) }
        }

        should("return a failed result when the client call fails") {
            val testWebhookUrl = "https://an-url.com"
            val expectedMessage = SlackWebhookMessage(text = Messages.integrationTestMessage())
            val config = mockk<SlackNotificationConfig>(relaxed = true) {
                every { webhookUrl } returns testWebhookUrl
            }
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) } returns Single.error(
                RuntimeException("Something went wrong")
            )

            val result = slackWebhookService.sendTestMessage(config).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("Something went wrong")

            verify(exactly = 4) { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) }
        }
    }
}) {
    @MockBean(SlackWebhookClient::class)
    fun slackWebhookClient(): SlackWebhookClient = mockk()
}
