package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.HttpMethod
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * These tests are meant to check if a wrongly configured MonitorConfig in the YAML files
 * really hinders the application from starting as expected.
 */
class MonitorConfigValidationTest : BehaviorSpec({

    given("a MonitorConfig bean") {

        `when`("name is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-blank-name")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getName - must not be blank"
            }
        }

        `when`("url is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-blank-url")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getUrl - must match " +
                    "\"^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]\""
            }
        }

        `when`("url is not a vadlid URL") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-invalid-url")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getUrl - must match " +
                    "\"^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]\""
            }
        }

        `when`("uptimeCheckInterval is less than 60 seconds") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-short-uptime-check-interval")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getUptimeCheckInterval - must be greater than or equal to 60"
            }
        }
    }
})

/**
 * These tests are meant to check if a MonitorConfig bean with default values
 * is created correctly when not all the properties are explicitly set in the YAML.
 *
 * It extends a DatabaseBehaviorSpec to delete the inserted monitor properly to not affect other tests
 */
@MicronautTest(startApplication = false)
class MonitorConfigDefaultValuesTest : DatabaseBehaviorSpec({

    given("a MonitorConfig bean") {

        `when`("not all the properties are explicitly set in the YAML") {
            val config = ApplicationContext.run("monitor-without-defaults")

            then("it should fall back to the right default values") {
                val monitorConfig = config.getBean(MonitorConfig::class.java)
                monitorConfig.enabled shouldBe true
                monitorConfig.sslCheckEnabled shouldBe false
                monitorConfig.requestMethod shouldBe HttpMethod.GET
                monitorConfig.pagerdutyIntegrationKey shouldBe null
                monitorConfig.latencyHistoryEnabled shouldBe true
                monitorConfig.forceNoCache shouldBe true
                monitorConfig.followRedirects shouldBe true
            }
        }
    }
})
