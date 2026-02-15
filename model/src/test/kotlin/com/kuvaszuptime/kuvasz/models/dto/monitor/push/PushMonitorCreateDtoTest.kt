package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.shouldHaveError
import com.kuvaszuptime.kuvasz.models.shouldHaveSingleError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator
import java.util.UUID

@MicronautTest(startApplication = false)
class PushMonitorCreateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    given("the validation setup of a PushMonitorCreateDto") {

        `when`("name is an empty string") {
            val dto = PushMonitorCreateDto(
                name = "",
                heartbeatInterval = 20,
                gracePeriod = 10,
                clientSecret = randomClientSecret(),
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "name",
                    message = MonitorValidationMessages.NAME_NOT_BLANK
                )
            }
        }

        `when`("heartbeatInterval is less than 10 seconds") {
            val dto = PushMonitorCreateDto(
                name = "fdafa",
                heartbeatInterval = 9,
                gracePeriod = 10,
                clientSecret = randomClientSecret(),
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "heartbeatInterval",
                    message = "Heartbeat interval must be at least 10 seconds"
                )
            }
        }

        `when`("gracePeriod is less than 0 seconds") {
            val dto = PushMonitorCreateDto(
                name = "fdagad",
                heartbeatInterval = 20,
                gracePeriod = -1,
                clientSecret = randomClientSecret(),
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "gracePeriod",
                    message = MonitorValidationMessages.GRACE_PERIOD_POSITIVE_OR_ZERO
                )
            }
        }

        `when`("clientSecret is an empty string") {
            val dto = PushMonitorCreateDto(
                name = "fdagad",
                heartbeatInterval = 20,
                gracePeriod = 0,
                clientSecret = "",
            )

            then("bean validation should signal an error") {
                with(validator.validate(dto)) {
                    shouldHaveError(
                        propertyPath = "clientSecret",
                        message = "Client secret must be at least 36 characters long"
                    )
                    shouldHaveError(
                        propertyPath = "clientSecret",
                        message = "Client secret must not be blank"
                    )
                }
            }
        }

        `when`("clientSecret is a blank string") {
            val dto = PushMonitorCreateDto(
                name = "fdagad",
                heartbeatInterval = 20,
                gracePeriod = 0,
                clientSecret = " ".repeat(36),
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "clientSecret",
                    message = "Client secret must not be blank"
                )
            }
        }

        `when`("clientSecret is too short") {
            val dto = PushMonitorCreateDto(
                name = "fdagad",
                heartbeatInterval = 20,
                gracePeriod = 0,
                clientSecret = "a".repeat(35),
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "clientSecret",
                    message = "Client secret must be at least 36 characters long"
                )
            }
        }
    }
})

class PushMonitorCreateDtoDefaultsTest : BehaviorSpec({

    given("a MonitorCreateDto with default values") {
        val dto = PushMonitorCreateDto(
            name = "dfa",
            heartbeatInterval = 20,
            clientSecret = randomClientSecret(),
        )

        then("the default values should be set correctly") {
            dto.enabled shouldBe PushMonitorDefaults.MONITOR_ENABLED
            dto.gracePeriod shouldBe PushMonitorDefaults.GRACE_PERIOD_SECONDS
            dto.integrations shouldBe emptyList()
            dto.failureCountThreshold shouldBe 1
        }
    }
})

fun randomClientSecret() = UUID.randomUUID().toString()
