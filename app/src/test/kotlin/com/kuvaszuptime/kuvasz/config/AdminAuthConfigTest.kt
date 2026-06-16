package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.testAppContext
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
                exception.message shouldContain "Admin password must be at least 12 characters"
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
                exception1.message shouldContain "Admin username must not be blank"
                exception2.message shouldContain "Admin password must not be blank"
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
                exception.message shouldContain "Admin username and password should not be equal"
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

    given("the API key validation - security enabled") {

        // A valid username/password pair, so that the context can otherwise start up
        val validAdminCredentials = mapOf(
            "micronaut.security.enabled" to true,
            "admin-auth.username" to "test-user",
            "admin-auth.password" to "validPassword123",
        )

        `when`("the API key is set but shorter than 16 characters") {
            val properties = PropertySource.of(
                "test",
                validAdminCredentials + mapOf("admin-auth.api-key" to "shortApiKey"),
            )
            then("ApplicationContext should throw a BeanInstantiationException") {
                val exception = shouldThrow<BeanInstantiationException> {
                    testAppContext(properties)
                }
                exception.message shouldContain "Admin API key must be at least 16 characters"
            }
        }

        `when`("the API key is blank") {
            val properties = PropertySource.of(
                "test",
                validAdminCredentials + mapOf("admin-auth.api-key" to ""),
            )
            then("ApplicationContext should not throw an exception, as the API key is optional") {
                shouldNotThrowAny { testAppContext(properties) }
            }
        }

        `when`("the API key is not set at all") {
            val properties = PropertySource.of(
                "test",
                validAdminCredentials,
            )
            then("ApplicationContext should not throw an exception, as the API key is optional") {
                shouldNotThrowAny { testAppContext(properties) }
            }
        }

        `when`("the API key is set and at least 16 characters long") {
            val properties = PropertySource.of(
                "test",
                validAdminCredentials + mapOf(
                    "admin-auth.api-key" to "validApiKeyvalidApiKeyvalidApiKeyvalidApiKey",
                ),
            )
            then("ApplicationContext should not throw an exception") {
                shouldNotThrowAny { testAppContext(properties) }
            }
        }
    }

    given("the MCP API key validation - security enabled") {

        // A valid username/password pair, so that the context can otherwise start up
        val validAdminCredentials = mapOf(
            "micronaut.security.enabled" to true,
            "admin-auth.username" to "test-user",
            "admin-auth.password" to "validPassword123",
        )

        `when`("the MCP API key is set but shorter than 16 characters") {
            val properties = PropertySource.of(
                "test",
                validAdminCredentials + mapOf("admin-auth.mcp-api-key" to "shortApiKey"),
            )
            then("ApplicationContext should throw a BeanInstantiationException") {
                val exception = shouldThrow<BeanInstantiationException> {
                    testAppContext(properties)
                }
                exception.message shouldContain "MCP API key must be at least 16 characters"
            }
        }

        `when`("the MCP API key is blank") {
            val properties = PropertySource.of(
                "test",
                validAdminCredentials + mapOf("admin-auth.mcp-api-key" to ""),
            )
            then("ApplicationContext should not throw an exception, as the MCP API key is optional") {
                shouldNotThrowAny { testAppContext(properties) }
            }
        }

        `when`("the MCP API key is not set at all") {
            val properties = PropertySource.of(
                "test",
                validAdminCredentials,
            )
            then("ApplicationContext should not throw an exception, as the MCP API key is optional") {
                shouldNotThrowAny { testAppContext(properties) }
            }
        }

        `when`("the MCP API key is set and at least 16 characters long") {
            val properties = PropertySource.of(
                "test",
                validAdminCredentials + mapOf(
                    "admin-auth.mcp-api-key" to "validMcpKeyvalidMcpKeyvalidMcpKeyvalidMcpKey",
                ),
            )
            then("ApplicationContext should not throw an exception") {
                shouldNotThrowAny { testAppContext(properties) }
            }
        }
    }
})
