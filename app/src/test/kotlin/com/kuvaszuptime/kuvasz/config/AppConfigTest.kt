package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException

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

        given("an AppConfig's monitor external-write toggles") {
            `when`("push and ICMP external writes are disabled and re-enabled") {
                val appConfig = AppConfig()
                appConfig.disablePushMonitorExternalWrite()
                appConfig.disableIcmpMonitorExternalWrite()

                then("the disabled flags should be set, then cleared by the enable methods") {
                    appConfig.isPushMonitorExternalWriteDisabled() shouldBe true
                    appConfig.isIcmpMonitorExternalWriteDisabled() shouldBe true

                    appConfig.enablePushMonitorExternalWrite()
                    appConfig.enableIcmpMonitorExternalWrite()

                    appConfig.isPushMonitorExternalWriteDisabled() shouldBe false
                    appConfig.isIcmpMonitorExternalWriteDisabled() shouldBe false
                }
            }

            `when`("HTTP external write is disabled and re-enabled") {
                val appConfig = AppConfig()
                appConfig.disableHttpMonitorExternalWrite()

                then("the flag should be set, then cleared by the enable method") {
                    appConfig.isHttpMonitorExternalWriteDisabled() shouldBe true

                    appConfig.enableHttpMonitorExternalWrite()

                    appConfig.isHttpMonitorExternalWriteDisabled() shouldBe false
                }
            }
        }
    }
)
