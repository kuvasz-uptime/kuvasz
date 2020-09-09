package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.formatters.SlackTextFormatter
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.SlackWebhookService
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory
import javax.inject.Inject

@Context
@Requires(property = "handler-config.slack-event-handler.enabled", value = "true")
class SlackEventHandler @Inject constructor(
    slackWebhookService: SlackWebhookService,
    eventDispatcher: EventDispatcher
) : RTCMessageEventHandler(eventDispatcher, slackWebhookService) {

    override val logger = LoggerFactory.getLogger(SlackEventHandler::class.java)

    override val formatter = SlackTextFormatter
}
