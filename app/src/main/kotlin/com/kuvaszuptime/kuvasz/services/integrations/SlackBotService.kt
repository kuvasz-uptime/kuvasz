package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackBotNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackApiResponse
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackAttachment
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackMarkdownText
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackPostMessageRequest
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackReactionRequest
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackSectionBlock
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackUpdateMessageRequest
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton
import java.net.URI

/**
 * A minimal client of the Slack Web API. Every call is retried with a longer backoff than the other integrations,
 * because a mass recovery can fire several calls per monitor at once, which Slack answers with HTTP 429 until its
 * per-minute rate limit window is over. The errors Slack reports in the response body are never retried.
 */
@Singleton
@Requires(bean = SlackBotNotificationConfig::class)
class SlackBotClient(
    @param:Client private val client: HttpClient,
    @param:Value("\${internal.slack-api-base-uri:https://slack.com/api}") private val apiBaseUri: URI,
) {

    @Retryable(
        attempts = RETRY_ATTEMPTS,
        delay = RETRY_DELAY,
        multiplier = RETRY_MULTIPLIER,
        excludes = [SlackApiException::class],
    )
    fun postMessage(botToken: String, request: SlackPostMessageRequest): Single<SlackApiResponse> =
        call(SlackApiMethod.CHAT_POST_MESSAGE, botToken, request)

    @Retryable(
        attempts = RETRY_ATTEMPTS,
        delay = RETRY_DELAY,
        multiplier = RETRY_MULTIPLIER,
        excludes = [SlackApiException::class],
    )
    fun updateMessage(botToken: String, request: SlackUpdateMessageRequest): Single<SlackApiResponse> =
        call(SlackApiMethod.CHAT_UPDATE, botToken, request)

    @Retryable(
        attempts = RETRY_ATTEMPTS,
        delay = RETRY_DELAY,
        multiplier = RETRY_MULTIPLIER,
        excludes = [SlackApiException::class],
    )
    fun addReaction(botToken: String, request: SlackReactionRequest): Single<SlackApiResponse> =
        call(SlackApiMethod.REACTIONS_ADD, botToken, request)

    @Retryable(
        attempts = RETRY_ATTEMPTS,
        delay = RETRY_DELAY,
        multiplier = RETRY_MULTIPLIER,
        excludes = [SlackApiException::class],
    )
    fun removeReaction(botToken: String, request: SlackReactionRequest): Single<SlackApiResponse> =
        call(SlackApiMethod.REACTIONS_REMOVE, botToken, request)

    private fun call(method: SlackApiMethod, botToken: String, body: Any): Single<SlackApiResponse> {
        val req = HttpRequest.POST("$apiBaseUri/${method.literal}".toUri(), body).bearerAuth(botToken)

        return Single.fromPublisher(client.retrieve(req, SlackApiResponse::class.java))
            .flatMap { response ->
                if (response.ok) {
                    Single.just(response)
                } else {
                    Single.error(SlackApiException(method, response.error, response.needed))
                }
            }
    }

    companion object {
        private const val RETRY_ATTEMPTS = "5"

        // Configurable only to let the tests run without waiting for a whole minute
        private const val RETRY_DELAY = "\${internal.slack-bot-retry-delay:2s}"
        private const val RETRY_MULTIPLIER = "2"
    }
}

@Singleton
@Requires(bean = SlackBotClient::class)
class SlackBotService(
    private val client: SlackBotClient,
) : TextMessageService, TestableNotificationService<SlackBotNotificationConfig> {

    /**
     * Posts a message with a colored side bar to the configured channel and returns its timestamp, which is the ID of
     * the message in the channel.
     */
    fun postMessage(integrationConfig: SlackBotNotificationConfig, text: String, color: String?): Single<String> =
        client.postMessage(
            botToken = integrationConfig.botToken,
            request = SlackPostMessageRequest(
                channel = integrationConfig.channelId,
                attachments = listOf(text.toAttachment(color)),
                threadTs = null,
                unfurlLinks = false,
                unfurlMedia = false,
            ),
        ).map { response -> response.ts.orEmpty() }

    override fun sendMessage(integrationConfig: IntegrationConfig, content: String): Single<String> =
        (integrationConfig as? SlackBotNotificationConfig)?.let { slackBotConfig ->
            postMessage(slackBotConfig, content, color = null)
        } ?: Single.error(
            SlackBotConfigurationException("Invalid integration configuration for Slack Bot: $integrationConfig")
        )

    override fun sendTestMessage(integrationConfig: SlackBotNotificationConfig): Single<NotificationTestResult> =
        sendMessage(integrationConfig, Messages.integrationTestMessage()).toNotificationTestResult()

    private fun String.toAttachment(color: String?) = SlackAttachment(
        color = color,
        fallback = this,
        blocks = listOf(SlackSectionBlock(SlackMarkdownText(this))),
    )

}

class SlackBotConfigurationException(override val message: String?) : Exception(message)

enum class SlackApiMethod(val literal: String) {
    CHAT_POST_MESSAGE("chat.postMessage"),
    CHAT_UPDATE("chat.update"),
    REACTIONS_ADD("reactions.add"),
    REACTIONS_REMOVE("reactions.remove"),
}

/**
 * An error Slack reported in the body of an otherwise successful response, extended with a hint about the usual fix
 */
class SlackApiException(
    method: SlackApiMethod,
    val error: String?,
    needed: String?,
) : Exception(
    listOfNotNull(Messages.slackApiError(method.literal, error ?: "unknown_error"), error.toHint(needed)).joinToString(". ")
)

private fun String?.toHint(needed: String?): String? = when (this) {
    "not_in_channel" -> Messages.slackApiErrorHintNotInChannel()
    "channel_not_found" -> Messages.slackApiErrorHintChannelNotFound()
    "missing_scope" -> Messages.slackApiErrorHintMissingScope(needed.orEmpty())
    "invalid_auth", "not_authed", "token_revoked", "token_expired", "account_inactive" ->
        Messages.slackApiErrorHintInvalidToken()
    else -> null
}
