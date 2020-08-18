package com.kuvaszuptime.kuvasz.config

import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException

class SMTPMailerConfigTest : BehaviorSpec({
    given("an SMTPMailerConfig bean") {
        `when`("the SMTP host does not exists") {
            val properties = PropertySource.of(
                "test",
                mapOf(
                    "handler-config.smtp-event-handler.enabled" to "true",
                    "smtp-config.host" to "localhost",
                    "smtp-config.port" to "123"
                )
            )
            then("ApplicationContext should throw a BeanInstantiationException") {
                val exception = shouldThrow<BeanInstantiationException> {
                    ApplicationContext.run(properties)
                }
                exceptionToMessage(exception) shouldContain "Error when trying to open connection to the server"
            }
        }
    }
})
