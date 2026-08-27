package com.kuvaszuptime.kuvasz.models.handlers

enum class IntegrationType(
    val identifier: String,
    val displayName: String = identifier.replaceFirstChar(Char::uppercase),
) {
    EMAIL(EmailNotificationConfig.IDENTIFIER),
    PAGERDUTY(PagerdutyConfig.IDENTIFIER, displayName = "PagerDuty"),
    SLACK(SlackNotificationConfig.IDENTIFIER),
    TELEGRAM(TelegramNotificationConfig.IDENTIFIER),
    DISCORD(DiscordNotificationConfig.IDENTIFIER),
    MS_TEAMS(MsTeamsNotificationConfig.IDENTIFIER, displayName = "Microsoft Teams"),
    WEBHOOK(WebhookNotificationConfig.IDENTIFIER);

    companion object {
        fun fromIdentifier(identifier: String): IntegrationType? =
            entries.find { it.identifier == identifier }
    }
}
