package com.kuvaszuptime.kuvasz.models.dto.monitor.http

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import com.kuvaszuptime.kuvasz.models.shouldHaveSingleError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator

@MicronautTest(startApplication = false)
class HttpMonitorCreateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    given("the validation setup of a HttpMonitorCreateDto") {

        `when`("name is an empty string") {
            val dto = HttpMonitorCreateDto(
                name = "",
                url = "https://example.com",
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "name",
                    message = MonitorValidationMessages.NAME_NOT_BLANK
                )
            }
        }

        `when`("url is an empty string") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "",
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "url",
                    message = MonitorValidationMessages.URL_PATTERN
                )
            }
        }

        `when`("url is not a valid URL") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "invalid-url",
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "url",
                    message = MonitorValidationMessages.URL_PATTERN
                )
            }
        }

        `when`("uptimeCheckInterval is less than 5 seconds") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 4,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "uptimeCheckInterval",
                    message = "Uptime check interval must be at least 5 seconds"
                )
            }
        }

        `when`("sslExpiryThreshold is less than 0 days") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                sslExpiryThreshold = -1,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "sslExpiryThreshold",
                    message = MonitorValidationMessages.SSL_EXPIRY_THRESHOLD_POSITIVE_OR_ZERO
                )
            }
        }

        `when`("expectedStatusCodes contains a non-supported status code") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                expectedStatusCodes = listOf(500, 200)
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "expectedStatusCodes",
                    message = MonitorValidationMessages.SUPPORTED_STATUS_CODES
                )
            }
        }

        `when`("responseTimeThresholdMillis is negative") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                responseTimeThresholdMillis = -100
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "responseTimeThresholdMillis",
                    message = MonitorValidationMessages.RESPONSE_TIME_THRESHOLD_POSITIVE
                )
            }
        }

        `when`("responseTimeThresholdMillis is 0") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                responseTimeThresholdMillis = 0
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "responseTimeThresholdMillis",
                    message = MonitorValidationMessages.RESPONSE_TIME_THRESHOLD_POSITIVE
                )
            }
        }

        `when`("responseTimeThresholdMillis is greater than 30000") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                responseTimeThresholdMillis = 30001
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "responseTimeThresholdMillis",
                    message = "Response time threshold must be less than or equal to 30000 milliseconds"
                )
            }
        }

        `when`("requestHeaders contains an empty key") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                requestHeaders = mapOf("" to "value")
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "requestHeaders",
                    message = MonitorValidationMessages.VALID_HEADER_NAMES
                )
            }
        }

        `when`("requestHeaders contains a badly-formed key") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                requestHeaders = mapOf("1 -" to "value", "Valid-Header" to "value")
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "requestHeaders",
                    message = MonitorValidationMessages.VALID_HEADER_NAMES
                )
            }
        }

        `when`("requestHeaders contains a well-formed key") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                requestHeaders = mapOf("Valid!#$'*+-.^`|~_&%Header" to "value")
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("expectedHeaders contains an empty key") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                expectedHeaders = mapOf("" to "value")
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "expectedHeaders",
                    message = MonitorValidationMessages.VALID_HEADER_NAMES
                )
            }
        }

        `when`("expectedHeaders contains a badly-formed key") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                expectedHeaders = mapOf("1 -" to "value")
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "expectedHeaders",
                    message = MonitorValidationMessages.VALID_HEADER_NAMES
                )
            }
        }

        `when`("expectedHeaders contains a well-formed key") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                expectedHeaders = mapOf("Valid!#$'*+-.^`|~_&%Header" to "value")
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("requestBody is a well-formed JSON string") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                requestBody = "{ \"key\": \"value\" }"
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("requestBody is an invalid JSON string") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                requestBody = "{ key: value }" // Invalid JSON
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "requestBody",
                    message = ValidationMessages.WELL_FORMED_JSON_STRING
                )
            }
        }

        `when`("requestBody is null") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                requestBody = null
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("requestBody is an empty string") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                requestBody = ""
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("requestBody is an empty JSON object") {
            val dto = HttpMonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                requestBody = "{}"
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }
    }
})

class HttpMonitorCreateDtoDefaultsTest : BehaviorSpec({

    given("an HttpMonitorCreateDto with default values") {
        val dto = HttpMonitorCreateDto(
            name = "Test Monitor",
            url = "https://example.com",
            uptimeCheckInterval = 60,
        )

        then("the default values should be set correctly") {
            dto.enabled shouldBe HttpMonitorDefaults.MONITOR_ENABLED
            dto.sslCheckEnabled shouldBe HttpMonitorDefaults.SSL_CHECK_ENABLED
            dto.requestMethod shouldBe HttpMethod.valueOf(HttpMonitorDefaults.REQUEST_METHOD)
            dto.latencyHistoryEnabled shouldBe HttpMonitorDefaults.LATENCY_HISTORY_ENABLED
            dto.forceNoCache shouldBe HttpMonitorDefaults.FORCE_NO_CACHE
            dto.followRedirects shouldBe HttpMonitorDefaults.FOLLOW_REDIRECTS
            dto.sslExpiryThreshold shouldBe HttpMonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS
            dto.integrations shouldBe emptyList()
            dto.expectedStatusCodes shouldBe emptyList()
            dto.responseTimeThresholdMillis shouldBe null
            dto.expectedKeyword shouldBe null
            dto.expectedKeywordCaseSensitive shouldBe HttpMonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE
            dto.expectedKeywordNegated shouldBe HttpMonitorDefaults.EXPECTED_KEYWORD_NEGATED
            dto.requestHeaders.shouldBeEmpty()
            dto.expectedHeaders.shouldBeEmpty()
            dto.requestBody shouldBe null
            dto.failureCountThreshold shouldBe 1
        }
    }
})
