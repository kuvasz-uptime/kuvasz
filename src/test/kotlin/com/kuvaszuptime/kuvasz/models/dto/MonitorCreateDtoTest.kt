package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator
import jakarta.validation.ConstraintViolation

@MicronautTest(startApplication = false)
class MonitorCreateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    fun Set<ConstraintViolation<MonitorCreateDto>>.shouldHaveSingleError(
        propertyPath: String,
        message: String
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
                    message = "must not be blank"
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
                    message = "must match \"^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]\""
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
                    message = "must match \"^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]\""
                )
            }
        }

        `when`("uptimeCheckInterval is less than 60 seconds") {
            val dto = MonitorCreateDto(
                name = "Test Monitor",
                url = "https://example.com",
                uptimeCheckInterval = 59,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "uptimeCheckInterval",
                    message = "must be greater than or equal to 60"
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
                    message = "must be greater than or equal to 0"
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
            dto.pagerdutyIntegrationKey shouldBe null
            dto.sslExpiryThreshold shouldBe MonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS
        }
    }
})
