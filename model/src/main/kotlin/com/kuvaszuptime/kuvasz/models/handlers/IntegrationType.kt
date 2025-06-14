package com.kuvaszuptime.kuvasz.models.handlers

enum class IntegrationType(val identifier: String) {
    EMAIL(EmailNotificationConfig.IDENTIFIER),
    PAGERDUTY(PagerdutyConfig.IDENTIFIER),
    SLACK(SlackNotificationConfig.IDENTIFIER),
    TELEGRAM(TelegramNotificationConfig.IDENTIFIER);

    companion object {
        fun fromIdentifier(identifier: String): IntegrationType? =
            entries.find { it.identifier == identifier }
    }
}
