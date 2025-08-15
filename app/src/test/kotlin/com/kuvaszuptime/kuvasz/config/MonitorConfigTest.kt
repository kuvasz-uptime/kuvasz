package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.MonitorDefaults
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
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
                    "MonitorConfig.getName - ${ValidationMessages.NAME_NOT_BLANK}"
            }
        }

        `when`("url is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-blank-url")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getUrl - ${ValidationMessages.URL_PATTERN}"
            }
        }

        `when`("url is not a valid URL") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-invalid-url")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getUrl - ${ValidationMessages.URL_PATTERN}"
            }
        }

        `when`("uptimeCheckInterval is less than 60 seconds") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-short-uptime-check-interval")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getUptimeCheckInterval - Uptime check interval must be at least 5 seconds"
            }
        }

        `when`("sslExpiryThreshold is less than 0 day") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-negative-ssl-expiry-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getSslExpiryThreshold - ${ValidationMessages.SSL_EXPIRY_THRESHOLD_POSITIVE_OR_ZERO}"
            }
        }

        `when`("expectedStatusCodes contains an unsupported status code") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-invalid-status-code")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "getExpectedStatusCodes - ${ValidationMessages.SUPPORTED_STATUS_CODES}"
            }
        }

        `when`("responseTimeThresholdMillis is invalid") {
            val exception = shouldThrow<BeanInstantiationException> {
                ApplicationContext.run("monitor-invalid-response-time-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "MonitorConfig.getResponseTimeThresholdMillis - " +
                    "Response time threshold must be less than or equal to 30000 milliseconds"
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
                monitorConfig.latencyHistoryEnabled shouldBe MonitorDefaults.LATENCY_HISTORY_ENABLED
                monitorConfig.forceNoCache shouldBe MonitorDefaults.FORCE_NO_CACHE
                monitorConfig.followRedirects shouldBe MonitorDefaults.FOLLOW_REDIRECTS
                monitorConfig.sslExpiryThreshold shouldBe MonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS
                monitorConfig.integrations.shouldBeNull()
                monitorConfig.expectedStatusCodes.shouldBeNull()
                monitorConfig.responseTimeThresholdMillis.shouldBeNull()
                monitorConfig.expectedKeyword.shouldBeNull()
                monitorConfig.expectedKeywordCaseSensitive shouldBe MonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE
                monitorConfig.expectedKeywordNegated shouldBe MonitorDefaults.EXPECTED_KEYWORD_NEGATED
            }
        }
    }
})
