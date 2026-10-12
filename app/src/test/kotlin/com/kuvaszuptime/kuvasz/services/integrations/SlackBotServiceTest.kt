package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.SlackBotNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackApiResponse
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackAttachment
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackMarkdownText
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackPostMessageRequest
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackSectionBlock
import io.kotest.assertions.fail
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class SlackBotServiceTest(
    private val client: SlackBotClient,
    private val slackBotService: SlackBotService,
) : ShouldSpec({

    val config = mockk<SlackBotNotificationConfig> {
        every { botToken } returns "xoxb-test"
        every { channelId } returns "C0123"
    }

    fun expectedRequest(text: String, color: String?) = SlackPostMessageRequest(
        channel = "C0123",
        attachments = listOf(
            SlackAttachment(
                color = color,
                fallback = text,
                blocks = listOf(SlackSectionBlock(SlackMarkdownText(text))),
            )
        ),
        threadTs = null,
        unfurlLinks = false,
        unfurlMedia = false,
    )

    context("postMessage") {

        should("post a colored message to the configured channel and return its timestamp") {
            val mockClient = getMock(client)
            every { mockClient.postMessage("xoxb-test", expectedRequest("*Down*", "#d63939")) } returns
                Single.just(SlackApiResponse(ok = true, error = null, needed = null, ts = "1700000000.000100"))

            slackBotService.postMessage(config, "*Down*", "#d63939").blockingGet() shouldBe "1700000000.000100"

            verify(exactly = 1) {
                mockClient.postMessage("xoxb-test", expectedRequest("*Down*", "#d63939"))
            }
        }
    }

    context("sendMessage") {

        should("fail if the given integration is not a Slack Bot one") {
            slackBotService.sendMessage(mockk<SlackNotificationConfig>(), "text").blockingSubscribe(
                { fail("Should not succeed") },
                { error -> error.shouldBeInstanceOf<SlackBotConfigurationException>() }
            )
        }
    }

    context("sendTestMessage") {

        should("post the test message without a color to the configured channel") {
            val request = expectedRequest(Messages.integrationTestMessage(), color = null)
            val mockClient = getMock(client)
            every { mockClient.postMessage("xoxb-test", request) } returns
                Single.just(SlackApiResponse(ok = true, error = null, needed = null, ts = "1700000000.000100"))

            val result = slackBotService.sendTestMessage(config).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()
            verify(exactly = 1) { mockClient.postMessage("xoxb-test", request) }
        }

        should("return a failed result with the hint of the error Slack reported, without retrying it") {
            val request = expectedRequest(Messages.integrationTestMessage(), color = null)
            val mockClient = getMock(client)
            every { mockClient.postMessage("xoxb-test", request) } returns
                Single.error(SlackApiException(SlackApiMethod.CHAT_POST_MESSAGE, "not_in_channel", null))

            val result = slackBotService.sendTestMessage(config).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage(
                Messages.slackApiError("chat.postMessage", "not_in_channel") + ". " +
                    Messages.slackApiErrorHintNotInChannel()
            )
            verify(exactly = 1) { mockClient.postMessage("xoxb-test", request) }
        }

        should("return a failed result after retrying a failing request") {
            val request = expectedRequest(Messages.integrationTestMessage(), color = null)
            val mockClient = getMock(client)
            every { mockClient.postMessage("xoxb-test", request) } returns
                Single.error(RuntimeException("Something went wrong"))

            val result = slackBotService.sendTestMessage(config).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("Something went wrong")
            verify(exactly = 6) { mockClient.postMessage("xoxb-test", request) }
        }
    }
}) {
    @MockBean(SlackBotClient::class)
    fun slackBotClient(): SlackBotClient = mockk()
}
