package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDefaults
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * These tests are meant to check if a wrongly configured HttpMonitorConfig in the YAML files
 * really hinders the application from starting as expected.
 */
class HttpMonitorConfigValidationTest : BehaviorSpec({

    given("an HttpMonitorConfig bean") {

        `when`("name is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-blank-name")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getName - ${MonitorValidationMessages.NAME_NOT_BLANK}"
            }
        }

        `when`("url is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-blank-url")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getUrl - ${MonitorValidationMessages.URL_PATTERN}"
            }
        }

        `when`("url is not a valid URL") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-invalid-url")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getUrl - ${MonitorValidationMessages.URL_PATTERN}"
            }
        }

        `when`("uptimeCheckInterval is less than 60 seconds") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-short-uptime-check-interval")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getUptimeCheckInterval - Uptime check interval must be at least 5 seconds"
            }
        }

        `when`("sslExpiryThreshold is less than 0 day") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-negative-ssl-expiry-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getSslExpiryThreshold - " +
                    MonitorValidationMessages.SSL_EXPIRY_THRESHOLD_POSITIVE_OR_ZERO
            }
        }

        `when`("failureCountThreshold is less than 1") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("http-monitor-zero-failure-count-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getFailureCountThreshold - " +
                    MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE
            }
        }

        `when`("expectedStatusCodes contains an unsupported status code") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-invalid-status-code")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "getExpectedStatusCodes - ${MonitorValidationMessages.SUPPORTED_STATUS_CODES}"
            }
        }

        `when`("responseTimeThresholdMillis is invalid") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-invalid-response-time-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getResponseTimeThresholdMillis - " +
                    "Response time threshold must be less than or equal to 30000 milliseconds"
            }
        }

        `when`("requestHeaders contains an invalid header") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-invalid-request-header")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getRequestHeaders - ${ValidationMessages.VALID_HEADER_NAMES}"
            }
        }

        `when`("expectedHeaders contains an invalid header") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-invalid-expected-header")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getExpectedHeaders - ${ValidationMessages.VALID_HEADER_NAMES}"
            }
        }

        `when`("requestBody is not a well-formed JSON") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("monitor-invalid-request-body")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "HttpMonitorConfig.getRequestBody - ${ValidationMessages.WELL_FORMED_JSON_STRING}"
            }
        }
    }
})

/**
 * These tests are meant to check if an HttpMonitorConfig bean with default values
 * is created correctly when not all the properties are explicitly set in the YAML.
 *
 * It extends a DatabaseBehaviorSpec to delete the inserted monitor properly to not affect other tests
 */
@MicronautTest(startApplication = false, environments = ["monitor-without-defaults"])
class HttpMonitorConfigDefaultValuesTest(applicationContext: ApplicationContext) : DatabaseBehaviorSpec({

    given("an HttpMonitorConfig bean") {

        `when`("not all the properties are explicitly set in the YAML") {

            then("it should fall back to the right default values") {
                val monitorConfig = applicationContext.getBean<HttpMonitorConfig>()
                monitorConfig.enabled shouldBe HttpMonitorDefaults.MONITOR_ENABLED
                monitorConfig.sensitiveUrl shouldBe HttpMonitorDefaults.SENSITIVE_URL
                monitorConfig.sslCheckEnabled shouldBe HttpMonitorDefaults.SSL_CHECK_ENABLED
                monitorConfig.requestMethod shouldBe HttpMethod.valueOf(HttpMonitorDefaults.REQUEST_METHOD)
                monitorConfig.latencyHistoryEnabled shouldBe HttpMonitorDefaults.LATENCY_HISTORY_ENABLED
                monitorConfig.forceNoCache shouldBe HttpMonitorDefaults.FORCE_NO_CACHE
                monitorConfig.followRedirects shouldBe HttpMonitorDefaults.FOLLOW_REDIRECTS
                monitorConfig.sslExpiryThreshold shouldBe HttpMonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS
                monitorConfig.integrations.shouldBeNull()
                monitorConfig.expectedStatusCodes.shouldBeNull()
                monitorConfig.responseTimeThresholdMillis.shouldBeNull()
                monitorConfig.expectedKeyword.shouldBeNull()
                monitorConfig.expectedKeywordCaseSensitive shouldBe HttpMonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE
                monitorConfig.expectedKeywordNegated shouldBe HttpMonitorDefaults.EXPECTED_KEYWORD_NEGATED
                monitorConfig.requestHeaders.shouldNotBeNull().shouldBeEmpty()
                monitorConfig.expectedHeaders.shouldNotBeNull().shouldBeEmpty()
                monitorConfig.requestBody.shouldBeNull()
                monitorConfig.failureCountThreshold shouldBe HttpMonitorDefaults.FAILURE_COUNT_THRESHOLD
            }
        }
    }
})
