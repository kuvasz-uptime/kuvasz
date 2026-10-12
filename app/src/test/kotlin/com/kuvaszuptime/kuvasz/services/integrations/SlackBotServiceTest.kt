package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.SlackBotNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackApiResponse
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackAttachment
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackMarkdownText
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackPostMessageRequest
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackReactionRequest
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackSectionBlock
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackUpdateMessageRequest
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

    fun attachment(text: String, color: String?) = SlackAttachment(
        color = color,
        fallback = text,
        blocks = listOf(SlackSectionBlock(SlackMarkdownText(text))),
    )

    fun expectedRequest(text: String, color: String?, threadTs: String? = null) = SlackPostMessageRequest(
        channel = "C0123",
        attachments = listOf(attachment(text, color)),
        threadTs = threadTs,
    )

    fun ok(ts: String?) = Single.just(SlackApiResponse(ok = true, error = null, needed = null, ts = ts))

    fun slackError(method: SlackApiMethod, error: String) =
        Single.error<SlackApiResponse>(SlackApiException(method, error, null))

    context("postMessage") {

        should("post a colored message to the configured channel and return its timestamp") {
            val mockClient = getMock(client)
            every { mockClient.postMessage("xoxb-test", expectedRequest("*Down*", "#d63939")) } returns
                Single.just(SlackApiResponse(ok = true, error = null, needed = null, ts = "1700000000.000100"))

            slackBotService.postMessage(config, "*Down*", SlackColor.RED).blockingGet() shouldBe "1700000000.000100"

            verify(exactly = 1) {
                mockClient.postMessage("xoxb-test", expectedRequest("*Down*", "#d63939"))
            }
        }
    }

    context("reply") {

        should("reply in the thread of the given root") {
            val mockClient = getMock(client)
            val request = expectedRequest("*Up*", "#2fb344", threadTs = "1.1")
            every { mockClient.postMessage("xoxb-test", request) } returns ok("2.2")

            slackBotService.reply(config, "1.1", "*Up*", SlackColor.GREEN).blockingGet() shouldBe
                SlackReply(ts = "2.2", inThread = true)

            verify(exactly = 1) { mockClient.postMessage("xoxb-test", request) }
        }

        val goneErrors = listOf(SlackError.THREAD_NOT_FOUND, SlackError.MESSAGE_NOT_FOUND, SlackError.CHANNEL_NOT_FOUND)
        goneErrors.forEach { goneError ->
            should("post to the channel instead, if the root is gone ($goneError)") {
                val mockClient = getMock(client)
                val threadRequest = expectedRequest("*Up*", "#2fb344", threadTs = "1.1")
                val channelRequest = expectedRequest("*Up*", "#2fb344")
                every { mockClient.postMessage("xoxb-test", threadRequest) } returns
                    slackError(SlackApiMethod.CHAT_POST_MESSAGE, goneError)
                every { mockClient.postMessage("xoxb-test", channelRequest) } returns ok("3.3")

                slackBotService.reply(config, "1.1", "*Up*", SlackColor.GREEN).blockingGet() shouldBe
                    SlackReply(ts = "3.3", inThread = false)

                verify(exactly = 1) { mockClient.postMessage("xoxb-test", threadRequest) }
                verify(exactly = 1) { mockClient.postMessage("xoxb-test", channelRequest) }
            }
        }

        should("fail without posting to the channel on any other error") {
            val mockClient = getMock(client)
            every { mockClient.postMessage("xoxb-test", any()) } returns
                slackError(SlackApiMethod.CHAT_POST_MESSAGE, SlackError.NOT_IN_CHANNEL)

            slackBotService.reply(config, "1.1", "*Up*", SlackColor.GREEN).blockingSubscribe(
                { fail("Should not succeed") },
                { error -> (error as SlackApiException).error shouldBe SlackError.NOT_IN_CHANNEL }
            )

            verify(exactly = 1) { mockClient.postMessage("xoxb-test", any()) }
        }
    }

    context("recolorRoot") {

        should("re-send the original text of the root with the new color") {
            val mockClient = getMock(client)
            val request = SlackUpdateMessageRequest(
                channel = "C0123",
                ts = "1.1",
                attachments = listOf(attachment("*Down*", "#2fb344")),
            )
            every { mockClient.updateMessage("xoxb-test", request) } returns ok("1.1")

            slackBotService.recolorRoot(config, "1.1", "*Down*", SlackColor.GREEN).blockingGet() shouldBe "1.1"

            verify(exactly = 1) { mockClient.updateMessage("xoxb-test", request) }
        }
    }

    context("addReaction and removeReaction") {

        should("react with the given emoji, without the colons around it") {
            val mockClient = getMock(client)
            val request = SlackReactionRequest(channel = "C0123", timestamp = "1.1", name = "white_check_mark")
            every { mockClient.addReaction("xoxb-test", request) } returns ok(null)
            every { mockClient.removeReaction("xoxb-test", request) } returns ok(null)

            slackBotService.addReaction(config, "1.1", ":white_check_mark:").blockingGet() shouldBe "1.1"
            slackBotService.removeReaction(config, "1.1", "white_check_mark").blockingGet() shouldBe "1.1"

            verify(exactly = 1) { mockClient.addReaction("xoxb-test", request) }
            verify(exactly = 1) { mockClient.removeReaction("xoxb-test", request) }
        }

        should("succeed if the reaction is already there, or not there anymore") {
            val mockClient = getMock(client)
            every { mockClient.addReaction("xoxb-test", any()) } returns
                slackError(SlackApiMethod.REACTIONS_ADD, SlackError.ALREADY_REACTED)
            every { mockClient.removeReaction("xoxb-test", any()) } returns
                slackError(SlackApiMethod.REACTIONS_REMOVE, SlackError.NO_REACTION)

            slackBotService.addReaction(config, "1.1", "rotating_light").blockingGet() shouldBe "1.1"
            slackBotService.removeReaction(config, "1.1", "rotating_light").blockingGet() shouldBe "1.1"
        }

        should("fail on any other error") {
            val mockClient = getMock(client)
            every { mockClient.addReaction("xoxb-test", any()) } returns
                slackError(SlackApiMethod.REACTIONS_ADD, "invalid_name")
            every { mockClient.removeReaction("xoxb-test", any()) } returns
                Single.error(RuntimeException("Something went wrong"))

            slackBotService.addReaction(config, "1.1", "nope").blockingSubscribe(
                { fail("Should not succeed") },
                { error -> (error as SlackApiException).error shouldBe "invalid_name" }
            )
            slackBotService.removeReaction(config, "1.1", "nope").blockingSubscribe(
                { fail("Should not succeed") },
                { error -> error.message shouldBe "Something went wrong" }
            )
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
                Single.error(SlackApiException(SlackApiMethod.CHAT_POST_MESSAGE, SlackError.NOT_IN_CHANNEL, null))

            val result = slackBotService.sendTestMessage(config).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage(
                Messages.slackApiError("chat.postMessage", SlackError.NOT_IN_CHANNEL) + ". " +
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
