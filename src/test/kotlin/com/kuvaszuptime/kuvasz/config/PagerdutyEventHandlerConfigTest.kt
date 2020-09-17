package com.kuvaszuptime.kuvasz.config

import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException

class PagerdutyEventHandlerConfigTest : BehaviorSpec(
    {
        given("a PagerdutyEventHandlerConfig bean") {
            `when`("there is no integration key in the configuration") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "handler-config.pagerduty-event-handler.enabled" to "true"
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        ApplicationContext.run(properties)
                    }
                    exceptionToMessage(exception) shouldContain
                            "Bean definition [com.kuvaszuptime.kuvasz.handlers.PagerdutyEventHandler] " +
                            "could not be loaded"
                }
            }

            `when`("integration key is an empty strings") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "handler-config.pagerduty-event-handler.enabled" to "true",
                        "handler-config.pagerduty-event-handler.integration-key" to ""
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        ApplicationContext.run(properties)
                    }
                    exceptionToMessage(exception) shouldContain
                            "Bean definition [com.kuvaszuptime.kuvasz.handlers.PagerdutyEventHandler] " +
                            "could not be loaded"
                }
            }
        }
    }
)
