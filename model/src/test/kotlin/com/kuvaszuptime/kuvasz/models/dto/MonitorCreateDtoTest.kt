package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator
import jakarta.validation.ConstraintViolation

@MicronautTest(startApplication = false)
class MonitorCreateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    fun Set<ConstraintViolation<MonitorCreateDto>>.shouldHaveSingleError(
        propertyPath: String,
        message: String,
    ) {
        this.size shouldBe 1
        this.first().let { error ->
            error.propertyPath.toString() shouldBe propertyPath
            error.message shouldBe message
        }
    }

    given("the validation setup of a MonitorCreateDto") {

        `when`("name is an empty string") {
            val dto = MonitorCreateDto(
                name = "",
                url = "https://example.com",
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "name",
                    message = ValidationMessages.NAME_NOT_BLANK
                )
            }
        }

        `when`("url is an empty string") {
            val dto = MonitorCreateDto(
                name = "Test Monitor",
                url = "",
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "url",
                    message = ValidationMessages.URL_PATTERN
                )
            }
        }

        `when`("url is not a valid URL") {
            val dto = MonitorCreateDto(
                name = "Test Monitor",
                url = "invalid-url",
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "url",
                    message = ValidationMessages.URL_PATTERN
                )
            }
        }

        `when`("uptimeCheckInterval is less than 5 seconds") {
            val dto = MonitorCreateDto(
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
            val dto = MonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                sslExpiryThreshold = -1,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "sslExpiryThreshold",
                    message = ValidationMessages.SSL_EXPIRY_THRESHOLD_POSITIVE_OR_ZERO
                )
            }
        }

        `when`("expectedStatusCodes contains a non-supported status code") {
            val dto = MonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                expectedStatusCodes = listOf(500, 200)
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "expectedStatusCodes",
                    message = ValidationMessages.SUPPORTED_STATUS_CODES
                )
            }
        }

        `when`("responseTimeThresholdMillis is negative") {
            val dto = MonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                responseTimeThresholdMillis = -100
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "responseTimeThresholdMillis",
                    message = ValidationMessages.RESPONSE_TIME_THRESHOLD_POSITIVE
                )
            }
        }

        `when`("responseTimeThresholdMillis is 0") {
            val dto = MonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 60,
                responseTimeThresholdMillis = 0
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "responseTimeThresholdMillis",
                    message = ValidationMessages.RESPONSE_TIME_THRESHOLD_POSITIVE
                )
            }
        }

        `when`("responseTimeThresholdMillis is greater than 30000") {
            val dto = MonitorCreateDto(
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
    }
})

class MonitorCreateDtoDefaultsTest : BehaviorSpec({

    given("a MonitorCreateDto with default values") {
        val dto = MonitorCreateDto(
            name = "Test Monitor",
            url = "https://example.com",
            uptimeCheckInterval = 60,
        )

        then("the default values should be set correctly") {
            dto.enabled shouldBe MonitorDefaults.MONITOR_ENABLED
            dto.sslCheckEnabled shouldBe MonitorDefaults.SSL_CHECK_ENABLED
            dto.requestMethod shouldBe HttpMethod.valueOf(MonitorDefaults.REQUEST_METHOD)
            dto.latencyHistoryEnabled shouldBe MonitorDefaults.LATENCY_HISTORY_ENABLED
            dto.forceNoCache shouldBe MonitorDefaults.FORCE_NO_CACHE
            dto.followRedirects shouldBe MonitorDefaults.FOLLOW_REDIRECTS
            dto.sslExpiryThreshold shouldBe MonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS
            dto.integrations shouldBe emptyList()
            dto.expectedStatusCodes shouldBe emptyList()
            dto.responseTimeThresholdMillis shouldBe null
            dto.expectedKeyword shouldBe null
            dto.expectedKeywordCaseSensitive shouldBe MonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE
            dto.expectedKeywordNegated shouldBe MonitorDefaults.EXPECTED_KEYWORD_NEGATED
        }
    }
})
