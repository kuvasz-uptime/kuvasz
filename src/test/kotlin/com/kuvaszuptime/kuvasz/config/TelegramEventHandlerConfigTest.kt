package com.kuvaszuptime.kuvasz.config

import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException

class TelegramEventHandlerConfigTest : BehaviorSpec(
    {
        given("a TelegramEventHandlerConfig bean") {
            `when`("there is no API token in the configuration") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "handler-config.telegram-event-handler.enabled" to "true",
                        "handler-config.telegram-event-handler.chat-id" to "chat-id"
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        ApplicationContext.run(properties)
                    }
                    exceptionToMessage(exception) shouldContain
                            "Bean definition [com.kuvaszuptime.kuvasz.handlers.TelegramEventHandler] " +
                            "could not be loaded"
                }
            }

            `when`("there is no chat ID in the configuration") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "handler-config.telegram-event-handler.enabled" to "true",
                        "handler-config.telegram-event-handler.token" to "your-token"
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        ApplicationContext.run(properties)
                    }
                    exceptionToMessage(exception) shouldContain
                            "Bean definition [com.kuvaszuptime.kuvasz.handlers.TelegramEventHandler] " +
                            "could not be loaded"
                }
            }

            `when`("chat ID and API token are empty strings") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "handler-config.telegram-event-handler.enabled" to "true",
                        "handler-config.telegram-event-handler.token" to "",
                        "handler-config.telegram-event-handler.chat-id" to ""
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        ApplicationContext.run(properties)
                    }
                    exceptionToMessage(exception) shouldContain
                            "Bean definition [com.kuvaszuptime.kuvasz.handlers.TelegramEventHandler] " +
                            "could not be loaded"
                }
            }
        }
    }
)
