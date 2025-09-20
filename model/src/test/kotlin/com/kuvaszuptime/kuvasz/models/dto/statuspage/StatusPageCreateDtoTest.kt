package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.models.dto.StatusPageValidationMessages
import com.kuvaszuptime.kuvasz.models.shouldHaveError
import com.kuvaszuptime.kuvasz.models.shouldHaveSingleError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator

@MicronautTest(startApplication = false)
class StatusPageCreateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    given("the validation setup of a MonitorCreateDto") {

        `when`("title is an empty string") {
            val dto = StatusPageCreateDto(
                title = "",
                slug = "valid-slug",
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "title",
                    message = StatusPageValidationMessages.TITLE_NOT_BLANK
                )
            }
        }

        `when`("slug is an empty string") {
            val dto = StatusPageCreateDto(
                title = "Valid Title",
                slug = "",
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveError(
                    propertyPath = "slug",
                    message = StatusPageValidationMessages.SLUG_NOT_BLANK
                )
            }
        }

        `when`("slug has invalid characters") {
            val dto = StatusPageCreateDto(
                title = "Valid Title",
                slug = "Invalid Slug!",
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "slug",
                    message = StatusPageValidationMessages.SLUG_PATTERN
                )
            }
        }

        `when`("all fields are valid") {
            val dto = StatusPageCreateDto(
                title = "Valid Title",
                slug = "valid-slug",
            )

            then("bean validation should not signal any error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }
    }
})

class StatusPageCreateDtoDefaultsTest : BehaviorSpec({

    given("a StatusPageCreateDto with default values") {
        val dto = StatusPageCreateDto(
            title = "Valid Title",
            slug = "valid-slug",
        )

        then("the default values should be set correctly") {
            dto.public shouldBe StatusPageDefaults.CUSTOM_PAGE_PUBLIC
            dto.monitors.shouldBeEmpty()
            dto.customLogoUrl shouldBe null
            dto.customFaviconUrl shouldBe null
        }
    }
})
