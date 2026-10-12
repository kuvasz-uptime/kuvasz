package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackAttachment
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackMarkdownText
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackPostMessageRequest
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackReactionRequest
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackSectionBlock
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackUpdateMessageRequest
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.fail
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.MatchType
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody
import org.mockserver.verify.VerificationTimes
import org.mockserver.model.MediaType as MockServerMediaType

class SlackBotClientTest : ShouldSpec({

    val mockServer = ClientAndServer.startClientAndServer(0)
    // A context of its own, because the API base URI has to point to the dynamic port of the mock server
    val client = testAppContext(
        mapOf("internal.slack-api-base-uri" to "http://localhost:${mockServer.port}/api"),
        "full-integrations-setup",
    ).getBean<SlackBotClient>()
    val botToken = "xoxb-test-token"

    afterSpec { mockServer.stop() }

    afterTest { mockServer.reset() }

    val attachment = SlackAttachment(
        color = "#d63939",
        fallback = "*Down*",
        blocks = listOf(SlackSectionBlock(SlackMarkdownText("*Down*"))),
    )

    fun slackRequest(method: String) = request()
        .withMethod(HttpMethod.POST.name)
        .withPath("/api/$method")
        .withHeader(HttpHeaders.AUTHORIZATION, "Bearer $botToken")
        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)

    fun okResponse(body: String = """{"ok":true}""") = response()
        .withStatusCode(HttpStatus.OK.code)
        .withContentType(MockServerMediaType.APPLICATION_JSON)
        .withBody(body)

    context("postMessage") {

        should("post the message as JSON with the bot token and return the timestamp of the posted message") {
            val request = slackRequest("chat.postMessage").withBody(
                JsonBody.json(
                    """
                    {
                        "channel": "C0123",
                        "attachments": [
                            {
                                "color": "#d63939",
                                "fallback": "*Down*",
                                "blocks": [{"type": "section", "text": {"type": "mrkdwn", "text": "*Down*"}}]
                            }
                        ],
                        "unfurl_links": false,
                        "unfurl_media": false
                    }
                    """.trimIndent(),
                    // Strict, so that a missing thread timestamp is not sent as null
                    MatchType.STRICT,
                )
            )
            mockServer.`when`(request)
                .respond(okResponse("""{"ok":true,"channel":"C0123","ts":"1700000000.000100","message":{}}"""))

            val response = client.postMessage(
                botToken,
                SlackPostMessageRequest(
                    channel = "C0123",
                    attachments = listOf(attachment),
                    threadTs = null,
                    unfurlLinks = false,
                    unfurlMedia = false,
                ),
            ).blockingGet()

            response.ok shouldBe true
            response.ts shouldBe "1700000000.000100"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("send the thread timestamp if there is any") {
            val request = slackRequest("chat.postMessage").withBody(
                JsonBody.json("""{"channel": "C0123", "thread_ts": "1700000000.000100"}""")
            )
            mockServer.`when`(request).respond(okResponse("""{"ok":true,"ts":"1700000001.000200"}"""))

            val response = client.postMessage(
                botToken,
                SlackPostMessageRequest(
                    channel = "C0123",
                    attachments = listOf(attachment),
                    threadTs = "1700000000.000100",
                    unfurlLinks = false,
                    unfurlMedia = false,
                ),
            ).blockingGet()

            response.ts shouldBe "1700000001.000200"
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("fail without retrying when Slack reports an error in the response body") {
            val request = slackRequest("chat.postMessage")
            mockServer.`when`(request).respond(okResponse("""{"ok":false,"error":"not_in_channel"}"""))

            client.postMessage(
                botToken,
                SlackPostMessageRequest(
                    channel = "C0123",
                    attachments = listOf(attachment),
                    threadTs = null,
                    unfurlLinks = false,
                    unfurlMedia = false,
                ),
            ).blockingSubscribe(
                { fail("Should not succeed") },
                { error ->
                    error.shouldBeInstanceOf<SlackApiException>()
                    error.error shouldBe SlackError.NOT_IN_CHANNEL
                    error.message shouldBe
                        Messages.slackApiError("chat.postMessage", SlackError.NOT_IN_CHANNEL) + ". " +
                        Messages.slackApiErrorHintNotInChannel()
                }
            )

            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("retry with a backoff when Slack responds with an HTTP error") {
            val request = slackRequest("chat.postMessage")
            mockServer.`when`(request).respond(response().withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.code))

            client.postMessage(
                botToken,
                SlackPostMessageRequest(
                    channel = "C0123",
                    attachments = listOf(attachment),
                    threadTs = null,
                    unfurlLinks = false,
                    unfurlMedia = false,
                ),
            ).blockingSubscribe(
                { fail("Should not succeed") },
                { error -> error.message shouldBe "Internal Server Error" }
            )

            // The original call + 5 retries
            mockServer.verify(request, VerificationTimes.exactly(6))
        }

        should("retry with a backoff when Slack rate limits the requests") {
            val request = slackRequest("chat.postMessage")
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.TOO_MANY_REQUESTS.code).withHeader("Retry-After", "1")
            )

            client.postMessage(
                botToken,
                SlackPostMessageRequest(
                    channel = "C0123",
                    attachments = listOf(attachment),
                    threadTs = null,
                    unfurlLinks = false,
                    unfurlMedia = false,
                ),
            ).blockingSubscribe(
                { fail("Should not succeed") },
                { error -> error.message shouldBe "Too Many Requests" }
            )

            mockServer.verify(request, VerificationTimes.exactly(6))
        }

        should("succeed if the rate limit is over before the retries run out") {
            val request = slackRequest("chat.postMessage")
            mockServer.`when`(request, Times.exactly(2)).respond(
                response().withStatusCode(HttpStatus.TOO_MANY_REQUESTS.code).withHeader("Retry-After", "1")
            )
            mockServer.`when`(request).respond(okResponse("""{"ok":true,"ts":"1700000000.000100"}"""))

            val response = client.postMessage(
                botToken,
                SlackPostMessageRequest(
                    channel = "C0123",
                    attachments = listOf(attachment),
                    threadTs = null,
                    unfurlLinks = false,
                    unfurlMedia = false,
                ),
            ).blockingGet()

            response.ts shouldBe "1700000000.000100"
            mockServer.verify(request, VerificationTimes.exactly(3))
        }
    }

    context("updateMessage") {

        should("replace the attachments of the given message") {
            val request = slackRequest("chat.update").withBody(
                JsonBody.json(
                    """
                    {
                        "channel": "C0123",
                        "ts": "1700000000.000100",
                        "attachments": [
                            {
                                "color": "#d63939",
                                "fallback": "*Down*",
                                "blocks": [{"type": "section", "text": {"type": "mrkdwn", "text": "*Down*"}}]
                            }
                        ]
                    }
                    """.trimIndent()
                )
            )
            mockServer.`when`(request).respond(okResponse("""{"ok":true,"ts":"1700000000.000100"}"""))

            val response = client.updateMessage(
                botToken,
                SlackUpdateMessageRequest(
                    channel = "C0123",
                    ts = "1700000000.000100",
                    attachments = listOf(attachment),
                ),
            ).blockingGet()

            response.ok shouldBe true
            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("fail without retrying when the message is gone") {
            val request = slackRequest("chat.update")
            mockServer.`when`(request).respond(okResponse("""{"ok":false,"error":"message_not_found"}"""))

            client.updateMessage(
                botToken,
                SlackUpdateMessageRequest(
                    channel = "C0123",
                    ts = "1700000000.000100",
                    attachments = listOf(attachment),
                ),
            ).blockingSubscribe(
                { fail("Should not succeed") },
                { error ->
                    error.shouldBeInstanceOf<SlackApiException>()
                    error.message shouldBe Messages.slackApiError("chat.update", SlackError.MESSAGE_NOT_FOUND)
                }
            )

            mockServer.verify(request, VerificationTimes.exactly(1))
        }
    }

    context("addReaction and removeReaction") {

        should("add a reaction to the given message") {
            val request = slackRequest("reactions.add").withBody(
                JsonBody.json("""{"channel": "C0123", "timestamp": "1700000000.000100", "name": "rotating_light"}""")
            )
            mockServer.`when`(request).respond(okResponse())

            client.addReaction(
                botToken,
                SlackReactionRequest(channel = "C0123", timestamp = "1700000000.000100", name = "rotating_light"),
            ).blockingGet().ok shouldBe true

            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("remove a reaction from the given message") {
            val request = slackRequest("reactions.remove").withBody(
                JsonBody.json("""{"channel": "C0123", "timestamp": "1700000000.000100", "name": "rotating_light"}""")
            )
            mockServer.`when`(request).respond(okResponse())

            client.removeReaction(
                botToken,
                SlackReactionRequest(channel = "C0123", timestamp = "1700000000.000100", name = "rotating_light"),
            ).blockingGet().ok shouldBe true

            mockServer.verify(request, VerificationTimes.exactly(1))
        }

        should("name the missing scope when the bot token is not allowed to react") {
            val request = slackRequest("reactions.add")
            mockServer.`when`(request).respond(
                okResponse(
                    """{"ok":false,"error":"missing_scope","needed":"reactions:write","provided":"chat:write"}"""
                )
            )

            client.addReaction(
                botToken,
                SlackReactionRequest(channel = "C0123", timestamp = "1700000000.000100", name = "rotating_light"),
            ).blockingSubscribe(
                { fail("Should not succeed") },
                { error ->
                    error.message shouldBe Messages.slackApiError("reactions.add", SlackError.MISSING_SCOPE) + ". " +
                        Messages.slackApiErrorHintMissingScope("reactions:write")
                }
            )

            mockServer.verify(request, VerificationTimes.exactly(1))
        }
    }

    context("SlackApiException") {

        should("give a hint about the usual fix of the known errors") {
            SlackApiException(SlackApiMethod.CHAT_POST_MESSAGE, SlackError.CHANNEL_NOT_FOUND, null).message shouldBe
                Messages.slackApiError("chat.postMessage", SlackError.CHANNEL_NOT_FOUND) + ". " +
                Messages.slackApiErrorHintChannelNotFound()
            val tokenErrors = listOf(
                SlackError.INVALID_AUTH,
                SlackError.NOT_AUTHED,
                SlackError.TOKEN_REVOKED,
                SlackError.TOKEN_EXPIRED,
                SlackError.ACCOUNT_INACTIVE,
            )
            tokenErrors.forEach { error ->
                SlackApiException(SlackApiMethod.CHAT_POST_MESSAGE, error, null).message shouldBe
                    Messages.slackApiError("chat.postMessage", error) + ". " + Messages.slackApiErrorHintInvalidToken()
            }
        }

        should("report the error without a hint if it's not a known one, or there is no error at all") {
            SlackApiException(SlackApiMethod.CHAT_UPDATE, "cant_update_message", null).message shouldBe
                Messages.slackApiError("chat.update", "cant_update_message")
            SlackApiException(SlackApiMethod.CHAT_UPDATE, null, null).message shouldBe
                Messages.slackApiError("chat.update", SlackError.UNKNOWN_ERROR)
        }
    }
})
