package com.kuvaszuptime.kuvasz.handlers

import io.micronaut.context.env.Environment
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.management.endpoint.info.InfoSource
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import javax.inject.Singleton

@Singleton
class HandlersInfoSource(private val environment: Environment) : InfoSource {

    override fun getSource(): Publisher<PropertySource> = Flowable.just(retrieveConfigurationInfo())

    private fun retrieveConfigurationInfo(): MapPropertySource {
        val handlerConfigs =
            mapOf(
                "log-event-handler.enabled" to environment.getBooleanProp(
                    "handler-config.log-event-handler.enabled",
                    false
                ),
                "smtp-event-handler.enabled" to environment.getBooleanProp(
                    "handler-config.smtp-event-handler.enabled",
                    false
                ),
                "slack-event-handler.enabled" to environment.getBooleanProp(
                    "handler-config.slack-event-handler.enabled",
                    false
                ),
                "telegram-event-handler.enabled" to environment.getBooleanProp(
                    "handler-config.telegram-event-handler.enabled",
                    false
                ),
                "pagerduty-event-handler.enabled" to environment.getBooleanProp(
                    "handler-config.pagerduty-event-handler.enabled",
                    false
                )
            )
        return MapPropertySource("handlers", mapOf("handlers" to handlerConfigs))
    }

    private fun Environment.getBooleanProp(key: String, defaultValue: Boolean): Boolean =
        getProperty(key, Boolean::class.java).orElse(defaultValue)
}
