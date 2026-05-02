package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.pebbletemplates.pebble.PebbleEngine
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
        .build()
}
