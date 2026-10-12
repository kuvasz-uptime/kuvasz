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
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
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
    @param:Value("\${internal.slack-api-base-uri:`https://slack.com/api`}") private val apiBaseUri: URI,
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
            .doOnError { error ->
                if (error is HttpClientResponseException && error.status == HttpStatus.TOO_MANY_REQUESTS) {
                    logger.debug(
                        "Slack rate limited ${method.literal}, it asked for a retry after " +
                            "${error.response.header(RETRY_AFTER_HEADER)}s"
                    )
                }
            }
            .flatMap { response ->
                if (response.ok) {
                    Single.just(response)
                } else {
                    Single.error(SlackApiException(method, response.error, response.needed))
                }
            }
    }

    companion object {
        private val logger = loggerFor<SlackBotClient>()

        private const val RETRY_AFTER_HEADER = "Retry-After"
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
     * Posts a message with a colored side bar to the configured channel, or into a thread of it if [threadTs] is
     * given, and returns its timestamp, which is the ID of the message in the channel.
     */
    fun postMessage(
        integrationConfig: SlackBotNotificationConfig,
        text: String,
        color: SlackColor?,
        threadTs: String? = null,
    ): Single<String> =
        client.postMessage(
            botToken = integrationConfig.botToken,
            request = SlackPostMessageRequest(
                channel = integrationConfig.channelId,
                attachments = listOf(text.toAttachment(color)),
                threadTs = threadTs,
            ),
        ).map { response -> response.ts.orEmpty() }

    /**
     * Replies in the thread of the given root message, or posts the message to the channel instead, if the root is
     * gone (e.g. it was deleted, or the channel of the integration has been changed since then).
     */
    fun reply(
        integrationConfig: SlackBotNotificationConfig,
        threadTs: String,
        text: String,
        color: SlackColor?,
    ): Single<SlackReply> =
        postMessage(integrationConfig, text, color, threadTs)
            .map { ts -> SlackReply(ts = ts, inThread = true) }
            .onErrorResumeNext { error ->
                if (error is SlackApiException && error.error in GONE_THREAD_ERRORS) {
                    logger.warn(
                        "The root message of thread $threadTs is gone (${error.error}), so the reply is posted to " +
                            "the channel instead"
                    )
                    postMessage(integrationConfig, text, color).map { ts -> SlackReply(ts = ts, inThread = false) }
                } else {
                    Single.error(error)
                }
            }

    fun recolorRoot(
        integrationConfig: SlackBotNotificationConfig,
        ts: String,
        text: String,
        color: SlackColor,
    ): Single<String> =
        client.updateMessage(
            botToken = integrationConfig.botToken,
            request = SlackUpdateMessageRequest(
                channel = integrationConfig.channelId,
                ts = ts,
                attachments = listOf(text.toAttachment(color)),
            ),
        ).map { ts }

    fun addReaction(integrationConfig: SlackBotNotificationConfig, ts: String, emoji: String): Single<String> =
        client.addReaction(integrationConfig.botToken, integrationConfig.reactionRequest(ts, emoji))
            .map { ts }
            .onErrorResumeNext { error -> error.ignoreSlackError(SlackError.ALREADY_REACTED, ts) }

    fun removeReaction(integrationConfig: SlackBotNotificationConfig, ts: String, emoji: String): Single<String> =
        client.removeReaction(integrationConfig.botToken, integrationConfig.reactionRequest(ts, emoji))
            .map { ts }
            .onErrorResumeNext { error -> error.ignoreSlackError(SlackError.NO_REACTION, ts) }

    override fun sendMessage(integrationConfig: IntegrationConfig, content: String): Single<String> =
        (integrationConfig as? SlackBotNotificationConfig)?.let { slackBotConfig ->
            postMessage(slackBotConfig, content, color = null)
        } ?: Single.error(
            SlackBotConfigurationException("Invalid integration configuration for Slack Bot: $integrationConfig")
        )

    override fun sendTestMessage(integrationConfig: SlackBotNotificationConfig): Single<NotificationTestResult> =
        sendMessage(integrationConfig, Messages.integrationTestMessage()).toNotificationTestResult()

    private fun String.toAttachment(color: SlackColor?) = SlackAttachment(
        color = color?.hex,
        fallback = this,
        blocks = listOf(SlackSectionBlock(SlackMarkdownText(this))),
    )

    // Slack accepts the names of the emojis only without the colons, which are easy to copy along from the UI
    private fun SlackBotNotificationConfig.reactionRequest(ts: String, emoji: String) =
        SlackReactionRequest(channel = channelId, timestamp = ts, name = emoji.trim(':'))

    private fun Throwable.ignoreSlackError(ignoredError: String, ts: String): Single<String> =
        if (this is SlackApiException && error == ignoredError) Single.just(ts) else Single.error(this)

    companion object {
        private val logger = loggerFor<SlackBotService>()

        private val GONE_THREAD_ERRORS = setOf(
            SlackError.THREAD_NOT_FOUND,
            SlackError.MESSAGE_NOT_FOUND,
            SlackError.CHANNEL_NOT_FOUND,
        )
    }
}

class SlackBotConfigurationException(override val message: String?) : Exception(message)

/**
 * The error codes Slack reports in the `error` field of its responses, that are handled in a special way
 * See: https://docs.slack.dev/reference/methods/chat.postMessage#errors
 */
internal object SlackError {
    const val NOT_IN_CHANNEL = "not_in_channel"
    const val CHANNEL_NOT_FOUND = "channel_not_found"
    const val MISSING_SCOPE = "missing_scope"
    const val INVALID_AUTH = "invalid_auth"
    const val NOT_AUTHED = "not_authed"
    const val TOKEN_REVOKED = "token_revoked"
    const val TOKEN_EXPIRED = "token_expired"
    const val ACCOUNT_INACTIVE = "account_inactive"
    const val THREAD_NOT_FOUND = "thread_not_found"
    const val MESSAGE_NOT_FOUND = "message_not_found"
    const val ALREADY_REACTED = "already_reacted"
    const val NO_REACTION = "no_reaction"

    // Not Slack's, the fallback if a failed response doesn't have an error at all
    const val UNKNOWN_ERROR = "unknown_error"
}

data class SlackReply(val ts: String, val inThread: Boolean)

enum class SlackColor(val hex: String) {
    RED("#d63939"),
    YELLOW("#f59f00"),
    GREEN("#2fb344"),
    BLUE("#066fd1"),
}

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
    listOfNotNull(
        Messages.slackApiError(method.literal, error ?: SlackError.UNKNOWN_ERROR),
        error.toHint(needed),
    ).joinToString(". ")
)

private fun String?.toHint(needed: String?): String? = when (this) {
    SlackError.NOT_IN_CHANNEL -> Messages.slackApiErrorHintNotInChannel()
    SlackError.CHANNEL_NOT_FOUND -> Messages.slackApiErrorHintChannelNotFound()
    SlackError.MISSING_SCOPE -> Messages.slackApiErrorHintMissingScope(needed.orEmpty())
    SlackError.INVALID_AUTH,
    SlackError.NOT_AUTHED,
    SlackError.TOKEN_REVOKED,
    SlackError.TOKEN_EXPIRED,
    SlackError.ACCOUNT_INACTIVE -> Messages.slackApiErrorHintInvalidToken()

    else -> null
}
