package com.kuvaszuptime.kuvasz.config

import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException

class AdminAuthConfigTest : BehaviorSpec({

    given("an AdminAuthConfig bean - security enabled") {
        `when`("password is less than 12 characters long") {
            val properties = PropertySource.of(
                "test",
                mapOf(
                    "micronaut.security.enabled" to true,
                    "admin-auth.username" to "test-user",
                    "admin-auth.password" to "tooShortPas",
                    "admin-auth.api-key" to "validApiKeyvalidApiKeyvalidApiKeyvalidApiKey",
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
                    "micronaut.security.enabled" to true,
                    "admin-auth.username" to "",
                    "admin-auth.password" to "test-password",
                    "admin-auth.api-key" to "validApiKeyvalidApiKeyvalidApiKeyvalidApiKey",
                )
            )
            val properties2 = PropertySource.of(
                "test",
                mapOf(
                    "micronaut.security.enabled" to true,
                    "admin-auth.username" to "test-user",
                    "admin-auth.password" to "",
                    "admin-auth.api-key" to "validApiKeyvalidApiKeyvalidApiKeyvalidApiKey",
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

        `when`("apiKey is less than 16 characters long") {
            val properties = PropertySource.of(
                "test",
                mapOf(
                    "micronaut.security.enabled" to true,
                    "admin-auth.username" to "test-user",
                    "admin-auth.password" to "validPassword123",
                    "admin-auth.api-key" to "shortApiKey",
                )
            )
            then("ApplicationContext should throw a BeanInstantiationException") {
                val exception = shouldThrow<BeanInstantiationException> {
                    ApplicationContext.run(properties)
                }
                exceptionToMessage(exception) shouldContain "AdminAuthConfig.p0 - size must be between 16"
            }
        }

        `when`("apiKey null or empty") {
            val properties = PropertySource.of(
                "test",
                mapOf(
                    "micronaut.security.enabled" to true,
                    "admin-auth.username" to "test-user",
                    "admin-auth.password" to "validPassword123",
                    "admin-auth.api-key" to "",
                )
            )
            val properties2 = PropertySource.of(
                "test",
                mapOf(
                    "micronaut.security.enabled" to true,
                    "admin-auth.username" to "test-user",
                    "admin-auth.password" to "validPassword123",
                    "admin-auth.api-key" to null,
                )
            )
            then("ApplicationContext should throw a BeanInstantiationException") {
                val exception1 = shouldThrow<BeanInstantiationException> {
                    ApplicationContext.run(properties)
                }
                val exception2 = shouldThrow<BeanInstantiationException> {
                    ApplicationContext.run(properties2)
                }
                exceptionToMessage(exception1) shouldContain "AdminAuthConfig.p0 - must not be blank"
                exceptionToMessage(exception2) shouldContain "AdminAuthConfig.p0 - must not be blank"
            }
        }

        `when`("username and password are the same") {
            val properties = PropertySource.of(
                "test",
                mapOf(
                    "micronaut.security.enabled" to true,
                    "admin-auth.username" to "samePassword123",
                    "admin-auth.password" to "samePassword123",
                    "admin-auth.api-key" to "validApiKeyvalidApiKeyvalidApiKeyvalidApiKey",
                )
            )
            then("ApplicationContext should throw a BeanInstantiationException") {
                val exception = shouldThrow<BeanInstantiationException> {
                    ApplicationContext.run(properties)
                }
                exceptionToMessage(exception) shouldContain "Admin username and password should not be equal"
            }
        }

        `when`("all properties are valid") {
            val properties = PropertySource.of(
                "test",
                mapOf(
                    "micronaut.security.enabled" to true,
                    "admin-auth.username" to "test-user",
                    "admin-auth.password" to "validPassword123",
                    "admin-auth.api-key" to "validApiKeyvalidApiKeyvalidApiKeyvalidApiKey",
                )
            )
            then("ApplicationContext should not throw an exception") {
                shouldNotThrowAny {
                    ApplicationContext.run(properties)
                }
            }
        }
    }

    given("an AdminAuthConfig bean - security disabled") {
        `when`("nothing is set") {
            val properties = PropertySource.of(
                "test",
                mapOf(
                    "micronaut.security.enabled" to false,
                )
            )
            then("ApplicationContext should not throw an exception") {
                shouldNotThrowAny {
                    ApplicationContext.run(properties)
                }
            }
        }
    }
})
