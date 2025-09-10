package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
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
                    testAppContext(properties)
                }
                exceptionToMessage(exception) shouldContain "Admin password must be at least 12 characters"
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
                    testAppContext(properties1)
                }
                val exception2 = shouldThrow<BeanInstantiationException> {
                    testAppContext(properties2)
                }
                exceptionToMessage(exception1) shouldContain "Admin username must not be blank"
                exceptionToMessage(exception2) shouldContain "Admin password must not be blank"
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
                    testAppContext(properties)
                }
                exceptionToMessage(exception) shouldContain "Admin API key must be at least 16 characters"
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
                    testAppContext(properties)
                }
                val exception2 = shouldThrow<BeanInstantiationException> {
                    testAppContext(properties2)
                }
                exceptionToMessage(exception1) shouldContain "Admin API key must not be blank"
                exceptionToMessage(exception2) shouldContain "Admin API key must not be blank"
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
                    testAppContext(properties)
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
                shouldNotThrowAny { testAppContext(properties) }
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
                shouldNotThrowAny { testAppContext(properties) }
            }
        }
    }
})
