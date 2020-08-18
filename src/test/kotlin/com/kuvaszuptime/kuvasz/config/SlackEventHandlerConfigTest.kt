package com.kuvaszuptime.kuvasz.config

import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException

class SlackEventHandlerConfigTest : BehaviorSpec({
    given("an SlackEventHandlerConfig bean") {
        `when`("there is no webhook URL in the configuration") {
            val properties = PropertySource.of(
                "test",
                mapOf(
                    "handler-config.slack-event-handler.enabled" to "true"
                )
            )
            then("ApplicationContext should throw a BeanInstantiationException") {
                val exception = shouldThrow<BeanInstantiationException> {
                    ApplicationContext.run(properties)
                }
                println(exceptionToMessage(exception))
                exceptionToMessage(exception) shouldContain
                        "Bean definition [com.kuvaszuptime.kuvasz.handlers.SlackEventHandler] could not be loaded"
            }
        }

        `when`("there the webhookUrl is not a valid URI") {
            val properties = PropertySource.of(
                "test",
                mapOf(
                    "handler-config.slack-event-handler.enabled" to "true",
                    "handler-config.slack-event-handler.webhook-url" to "jklfdjaklfjdalfda"
                )
            )
            then("ApplicationContext should throw a BeanInstantiationException") {
                val exception = shouldThrow<BeanInstantiationException> {
                    ApplicationContext.run(properties)
                }
                println(exceptionToMessage(exception))
                exceptionToMessage(exception) shouldContain
                        "Bean definition [com.kuvaszuptime.kuvasz.handlers.SlackEventHandler] could not be loaded"
            }
        }
    }
})
