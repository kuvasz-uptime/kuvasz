package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.formatters.TelegramTextFormatter
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.TelegramAPIService
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(bean = TelegramNotificationConfig::class)
class TelegramEventHandler(
    telegramAPIService: TelegramAPIService,
    eventDispatcher: EventDispatcher,
    integrationRepository: IntegrationRepository,
) : RTCMessageEventHandler(eventDispatcher, telegramAPIService, integrationRepository) {

    override val logger = loggerFor<TelegramEventHandler>()

    override val formatter = TelegramTextFormatter

    override val integrationType = IntegrationType.TELEGRAM
}
