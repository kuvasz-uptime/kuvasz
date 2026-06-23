package com.kuvaszuptime.kuvasz.models.dto.maintenance

import com.kuvaszuptime.kuvasz.models.dto.MaintenanceWindowValidationMessages
import com.kuvaszuptime.kuvasz.models.shouldHaveSingleError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator

@MicronautTest(startApplication = false)
class MaintenanceWindowCreateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    given("the validation setup of a MaintenanceWindowCreateDto") {

        `when`("the name is an empty string") {
            val dto = MaintenanceWindowCreateDto(name = "")

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "name",
                    message = MaintenanceWindowValidationMessages.NAME_NOT_BLANK,
                )
            }
        }

        `when`("the cron expression is invalid") {
            val dto = MaintenanceWindowCreateDto(name = "Test window", cron = "not a cron", duration = "PT1H")

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "cron",
                    message = MaintenanceWindowValidationMessages.CRON_INVALID,
                )
            }
        }

        `when`("the duration is not a positive ISO-8601 duration") {
            val dto = MaintenanceWindowCreateDto(name = "Test window", cron = "0 2 * * *", duration = "PT0S")

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "duration",
                    message = MaintenanceWindowValidationMessages.DURATION_INVALID,
                )
            }
        }

        `when`("the DTO is valid") {
            val dto = MaintenanceWindowCreateDto(name = "Test window", cron = "0 2 * * *", duration = "PT1H")

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }
    }
})

class MaintenanceWindowCreateDtoDefaultsTest : BehaviorSpec({

    given("a MaintenanceWindowCreateDto with default values") {
        val dto = MaintenanceWindowCreateDto(name = "Test window")

        then("the default values should be set correctly") {
            dto.description.shouldBeNull()
            dto.enabled shouldBe MaintenanceWindowDefaults.ENABLED
            dto.global shouldBe MaintenanceWindowDefaults.GLOBAL
            dto.showOnStatusPages shouldBe MaintenanceWindowDefaults.SHOW_ON_STATUS_PAGES
            dto.cron.shouldBeNull()
            dto.start.shouldBeNull()
            dto.duration.shouldBeNull()
            dto.monitors.shouldBeEmpty()
            dto.integrations.shouldBeEmpty()
        }
    }
})
