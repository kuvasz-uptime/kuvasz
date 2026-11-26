package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.webhook.GenericWebhookMessage
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.attributes.methodaccess.AllowlistMethodAccessValidator
import io.pebbletemplates.pebble.loader.StringLoader
import jakarta.inject.Singleton

@Factory
@Requires(bean = WebhookNotificationConfig::class)
class WebhookTemplateEngineFactory {

    @Singleton
    fun provideTemplateEngine(): PebbleEngine = PebbleEngine
        .Builder()
        .loader(StringLoader())
        .strictVariables(true)
        .methodAccessValidator(
            AllowlistMethodAccessValidator.builder()
                .allowDefaults()
                .allowTypes(GenericWebhookMessage::class.java)
                .build()
        )
        .build()
}
