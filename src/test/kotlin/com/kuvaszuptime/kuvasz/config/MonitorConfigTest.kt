package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.MonitorDefaults
import com.kuvaszuptime.kuvasz.testutils.getBean
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

        `when`("url is not a valid URL") {
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

        `when`("sslExpiryThreshold is less than 0 day") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-negative-ssl-expiry-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getSslExpiryThreshold - must be greater than or equal to 0"
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
@MicronautTest(startApplication = false, environments = ["monitor-without-defaults"])
class MonitorConfigDefaultValuesTest(applicationContext: ApplicationContext) : DatabaseBehaviorSpec({

    given("a MonitorConfig bean") {

        `when`("not all the properties are explicitly set in the YAML") {

            then("it should fall back to the right default values") {
                val monitorConfig = applicationContext.getBean<MonitorConfig>()
                monitorConfig.enabled shouldBe MonitorDefaults.MONITOR_ENABLED
                monitorConfig.sslCheckEnabled shouldBe MonitorDefaults.SSL_CHECK_ENABLED
                monitorConfig.requestMethod shouldBe HttpMethod.valueOf(MonitorDefaults.REQUEST_METHOD)
                monitorConfig.pagerdutyIntegrationKey shouldBe null
                monitorConfig.latencyHistoryEnabled shouldBe MonitorDefaults.LATENCY_HISTORY_ENABLED
                monitorConfig.forceNoCache shouldBe MonitorDefaults.FORCE_NO_CACHE
                monitorConfig.followRedirects shouldBe MonitorDefaults.FOLLOW_REDIRECTS
                monitorConfig.sslExpiryThreshold shouldBe MonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS
            }
        }
    }
})
