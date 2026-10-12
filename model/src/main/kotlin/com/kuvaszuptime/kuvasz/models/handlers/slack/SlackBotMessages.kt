package com.kuvaszuptime.kuvasz.models.handlers.slack

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

/**
 * Request body of Slack's chat.postMessage
 * See: https://docs.slack.dev/reference/methods/chat.postMessage
 */
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SlackPostMessageRequest(
    val channel: String,
    val attachments: List<SlackAttachment>,
    @field:JsonProperty("thread_ts")
    val threadTs: String?,
    @field:JsonProperty("unfurl_links")
    val unfurlLinks: Boolean,
    @field:JsonProperty("unfurl_media")
    val unfurlMedia: Boolean,
)

/**
 * Request body of Slack's chat.update
 * See: https://docs.slack.dev/reference/methods/chat.update
 */
@Introspected
data class SlackUpdateMessageRequest(
    val channel: String,
    val ts: String,
    val attachments: List<SlackAttachment>,
)

/**
 * Request body of Slack's reactions.add and reactions.remove
 * See: https://docs.slack.dev/reference/methods/reactions.add
 */
@Introspected
data class SlackReactionRequest(
    val channel: String,
    val timestamp: String,
    val name: String,
)

/**
 * A (legacy) message attachment, used only because it is the single way to give a message a colored side bar. Its
 * content is laid out with Block Kit, as Slack recommends even inside attachments.
 * See: https://docs.slack.dev/legacy/legacy-messaging/legacy-secondary-message-attachments/
 */
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SlackAttachment(
    val color: String?,
    // The plain text the notifications show
    val fallback: String,
    val blocks: List<SlackSectionBlock>,
)

@Introspected
data class SlackSectionBlock(
    val text: SlackMarkdownText,
) {
    val type = "section"
}

@Introspected
data class SlackMarkdownText(
    val text: String,
) {
    val type = "mrkdwn"
}

/**
 * The common envelope of every Slack Web API response. Slack reports most of the errors with an HTTP 200 and
 * `ok: false`, `needed` is set only for `missing_scope` errors.
 */
@Introspected
data class SlackApiResponse(
    val ok: Boolean,
    val error: String?,
    val needed: String?,
    val ts: String?,
)
