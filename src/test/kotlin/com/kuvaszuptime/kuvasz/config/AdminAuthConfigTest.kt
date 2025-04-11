package com.kuvaszuptime.kuvasz.config

import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException

class AdminAuthConfigTest : BehaviorSpec({

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
                exceptionToMessage(exception) shouldContain "AdminAuthConfig.p0 - size must be between 12"
            }
        }

        `when`("username or password is blank") {
            val properties1 = PropertySource.of(
                "test",
                mapOf(
                    "admin-auth.username" to "",
                    "admin-auth.password" to "test-password"
                )
            )
            val properties2 = PropertySource.of(
                "test",
                mapOf(
                    "admin-auth.username" to "test-user",
                    "admin-auth.password" to ""
                )
            )
            then("ApplicationContext should throw a BeanInstantiationException") {
                val exception1 = shouldThrow<BeanInstantiationException> {
                    ApplicationContext.run(properties1)
                }
                val exception2 = shouldThrow<BeanInstantiationException> {
                    ApplicationContext.run(properties2)
                }
                exceptionToMessage(exception1) shouldContain "AdminAuthConfig.p0 - must not be blank"
                exceptionToMessage(exception2) shouldContain "AdminAuthConfig.p0 - must not be blank"
            }
        }
    }
})
