package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.formatters.SlackTextFormatter
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.SlackWebhookService
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory

@Context
@Requires(bean = SlackNotificationConfig::class)
class SlackEventHandler(
    slackWebhookService: SlackWebhookService,
    eventDispatcher: EventDispatcher,
    integrationRepository: IntegrationRepository,
) : RTCMessageEventHandler(eventDispatcher, slackWebhookService, integrationRepository) {

    override val logger = LoggerFactory.getLogger(SlackEventHandler::class.java)

    override val formatter = SlackTextFormatter

    override val integrationType: IntegrationType = IntegrationType.SLACK
}
