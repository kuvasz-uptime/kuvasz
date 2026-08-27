package com.kuvaszuptime.kuvasz.models.handlers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.kuvaszuptime.kuvasz.models.events.formatters.MessageSeverity
import io.micronaut.core.annotation.Introspected

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MsTeamsMessage(
    val type: String = MESSAGE_TYPE,
    val attachments: List<MsTeamsAttachment>,
) {
    companion object {
        const val MESSAGE_TYPE = "message"

        fun of(card: AdaptiveCard) = MsTeamsMessage(attachments = listOf(MsTeamsAttachment(content = card)))
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MsTeamsAttachment(
    val contentType: String = ADAPTIVE_CARD_CONTENT_TYPE,
    val content: AdaptiveCard,
) {
    companion object {
        const val ADAPTIVE_CARD_CONTENT_TYPE = "application/vnd.microsoft.card.adaptive"
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdaptiveCard(
    val body: List<CardElement>,
    @field:JsonProperty("\$schema")
    val schema: String = SCHEMA_URL,
    val type: String = CARD_TYPE,
    val version: String = SCHEMA_VERSION,
    val msteams: Map<String, String> = mapOf("width" to "Full"),
) {
    companion object {
        const val SCHEMA_URL = "http://adaptivecards.io/schemas/adaptive-card.json"
        const val CARD_TYPE = "AdaptiveCard"
        const val SCHEMA_VERSION = "1.4"
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed interface CardElement {
    val type: String
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CardContainer(
    val items: List<CardElement>,
    val style: String,
    val bleed: Boolean = true,
    override val type: String = CONTAINER_TYPE,
) : CardElement {
    companion object {
        const val CONTAINER_TYPE = "Container"
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CardTextBlock(
    val text: String,
    val wrap: Boolean = true,
    val size: String? = null,
    val isSubtle: Boolean? = null,
    val spacing: String? = null,
    override val type: String = TEXT_BLOCK_TYPE,
) : CardElement {
    companion object {
        const val TEXT_BLOCK_TYPE = "TextBlock"
    }
}

/**
 * The Adaptive Card container styles Teams renders with a colored accent.
 **/
val MessageSeverity.containerStyle: String
    get() = when (this) {
        MessageSeverity.CRITICAL -> "attention"
        MessageSeverity.WARNING -> "warning"
        MessageSeverity.OK -> "good"
        MessageSeverity.INFO -> "accent"
    }
