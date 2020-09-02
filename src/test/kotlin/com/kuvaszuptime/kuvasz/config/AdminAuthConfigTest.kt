package com.kuvaszuptime.kuvasz.config

import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException

class AdminAuthConfigTest : BehaviorSpec(
    {
        given("an AdminAuthConfig bean") {
            `when`("password is less than 12 characters long") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "admin-auth.username" to "test-user",
                        "admin-auth.password" to "tooShortPas"
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        ApplicationContext.run(properties)
                    }
                    exceptionToMessage(exception) shouldContain "password - size must be between 12"
                }
            }

            `when`("username or password is blank") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "admin-auth.username" to "",
                        "admin-auth.password" to ""
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        ApplicationContext.run(properties)
                    }
                    exceptionToMessage(exception) shouldContain "username - must not be blank"
                    exceptionToMessage(exception) shouldContain "password - must not be blank"
                }
            }
        }
    }
)
