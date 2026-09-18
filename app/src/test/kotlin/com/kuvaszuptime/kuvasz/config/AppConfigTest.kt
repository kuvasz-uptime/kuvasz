package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.NoSuchBeanException

class AppConfigTest : BehaviorSpec(
    {
        given("an AppConfig bean") {
            `when`("there is an event-data-retention-days parameter with a null value") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.event-data-retention-days" to "null",
                        "app-config.latency-data-retention-days" to 7,
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(properties)
                    }
                    exception.message shouldContain
                        "Error resolving property value [app-config.event-data-retention-days]"
                }
            }

            `when`("there is an event-data-retention-days parameter with an exceptionally low value") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.event-data-retention-days" to "0",
                        "app-config.latency-data-retention-days" to 7,
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(properties)
                    }
                    exception.message shouldContain "Event data retention must be at least 1 days"
                }
            }

            `when`("there is a latency-data-retention-days parameter with a null value") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.event-data-retention-days" to 7,
                        "app-config.latency-data-retention-days" to "null"
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(properties)
                    }
                    exception.message shouldContain
                        "Error resolving property value [app-config.latency-data-retention-days]"
                }
            }

            `when`("there is a latency-data-retention-days parameter with an exceptionally low value") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.event-data-retention-days" to 7,
                        "app-config.latency-data-retention-days" to "0"
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(properties)
                    }
                    exception.message shouldContain "Latency data retention must be at least 1 days"
                }
            }
        }

        given("a ConnectivityCheckConfig bean") {

            `when`("the connectivity check is disabled") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.connectivity-check.enabled" to false,
                        "app-config.connectivity-check.targets" to listOf("definitely-not-a-target"),
                    )
                )
                then("the config bean is not provisioned, so its targets are never validated") {
                    val ctx = shouldNotThrowAny { testAppContext(properties) }

                    shouldThrow<NoSuchBeanException> { ctx.getBean(ConnectivityCheckConfig::class.java) }
                }
            }

            `when`("it is enabled with the defaults") {
                val properties = PropertySource.of(
                    "test",
                    mapOf("app-config.connectivity-check.enabled" to true)
                )
                then("the context starts up with the default targets and intervals") {
                    val ctx = shouldNotThrowAny { testAppContext(properties) }
                    val config = ctx.getBean(ConnectivityCheckConfig::class.java)

                    config.targets shouldBe listOf("1.1.1.1:53", "8.8.8.8:53")
                    config.intervalSeconds shouldBe 60L
                    config.timeoutSeconds shouldBe 5L
                    config.timeoutMillis shouldBe 5000
                    config.parsedTargets shouldBe listOf(
                        ConnectivityTarget("1.1.1.1", 53),
                        ConnectivityTarget("8.8.8.8", 53),
                    )
                }
            }

            `when`("it is enabled with an exceptionally low interval") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.connectivity-check.enabled" to true,
                        "app-config.connectivity-check.interval-seconds" to 4,
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(properties)
                    }
                    exception.message shouldContain "The connectivity check interval must be at least 5 seconds"
                }
            }

            `when`("it is enabled with an exceptionally low timeout") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.connectivity-check.enabled" to true,
                        "app-config.connectivity-check.timeout-seconds" to 0,
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(properties)
                    }
                    exception.message shouldContain "The connectivity check timeout must be at least 1 seconds"
                }
            }

            `when`("it is enabled without any target") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.connectivity-check.enabled" to true,
                        "app-config.connectivity-check.targets" to emptyList<String>(),
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(properties)
                    }
                    exception.message shouldContain "At least one connectivity check target must be configured"
                }
            }

            `when`("it is disabled without any target") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.connectivity-check.enabled" to false,
                        "app-config.connectivity-check.targets" to emptyList<String>(),
                    )
                )
                then("the context starts up, because the targets are irrelevant then") {
                    val ctx = shouldNotThrowAny { testAppContext(properties) }

                    shouldThrow<NoSuchBeanException> { ctx.getBean(ConnectivityCheckConfig::class.java) }
                }
            }

            `when`("the targets are set to an empty value") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "app-config.connectivity-check.enabled" to true,
                        "app-config.connectivity-check.targets" to "",
                    )
                )
                then("it is reported as a malformed target, because it binds to a single, empty entry") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(properties)
                    }
                    exception.message shouldContain
                        "Every connectivity check target must be in 'host:port' format"
                }
            }

            listOf(
                "no-port",
                "host:0",
                "host:70000",
                "host:not-a-number",
                ":53",
            ).forEach { invalidTarget ->
                `when`("it is enabled with a malformed target: '$invalidTarget'") {
                    val properties = PropertySource.of(
                        "test",
                        mapOf(
                            "app-config.connectivity-check.enabled" to true,
                            "app-config.connectivity-check.targets" to listOf(invalidTarget),
                        )
                    )
                    then("ApplicationContext should throw a BeanInstantiationException") {
                        val exception = shouldThrow<BeanInstantiationException> {
                            testAppContext(properties)
                        }
                        exception.message shouldContain
                            "Every connectivity check target must be in 'host:port' format"
                    }
                }
            }
        }
    }
)
