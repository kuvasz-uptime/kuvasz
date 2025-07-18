package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.formatters.DiscordTextFormatter
import com.kuvaszuptime.kuvasz.models.handlers.DiscordNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.services.DiscordWebhookService
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory

@Context
@Requires(bean = DiscordNotificationConfig::class)
class DiscordEventHandler(
    discordWebhookService: DiscordWebhookService,
    eventDispatcher: EventDispatcher,
    integrationRepository: IntegrationRepository,
) : RTCMessageEventHandler(eventDispatcher, discordWebhookService, integrationRepository) {
    override val logger = LoggerFactory.getLogger(DiscordEventHandler::class.java)

    override val formatter = DiscordTextFormatter

    override val integrationType: IntegrationType = IntegrationType.DISCORD
}
