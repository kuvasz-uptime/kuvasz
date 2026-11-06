package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.shouldHaveSingleError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.micronaut.core.annotation.Introspected
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator

private const val ERROR_MESSAGE = "entries must not be blank"

@Introspected
data class NotBlankEntriesHolder(
    @get:NotBlankEntries(message = ERROR_MESSAGE)
    val entries: List<String>?,
)

@MicronautTest(startApplication = false)
class NotBlankEntriesTest(validator: DefaultValidator) : BehaviorSpec({

    given("a list annotated with @NotBlankEntries") {

        `when`("it is null") {
            then("bean validation should signal an error") {
                validator.validate(NotBlankEntriesHolder(null))
                    .shouldHaveSingleError(propertyPath = "entries", message = ERROR_MESSAGE)
            }
        }

        `when`("it is empty") {
            then("bean validation should signal an error") {
                validator.validate(NotBlankEntriesHolder(emptyList()))
                    .shouldHaveSingleError(propertyPath = "entries", message = ERROR_MESSAGE)
            }
        }

        `when`("one of its entries is blank") {
            then("bean validation should signal an error") {
                validator.validate(NotBlankEntriesHolder(listOf("ops@kuvasz.dev", " ")))
                    .shouldHaveSingleError(propertyPath = "entries", message = ERROR_MESSAGE)
            }
        }

        `when`("one of its entries is an empty string") {
            then("bean validation should signal an error") {
                validator.validate(NotBlankEntriesHolder(listOf("")))
                    .shouldHaveSingleError(propertyPath = "entries", message = ERROR_MESSAGE)
            }
        }

        `when`("every entry has a non-blank value") {
            then("bean validation should NOT signal an error") {
                validator.validate(NotBlankEntriesHolder(listOf("ops@kuvasz.dev", "dev@kuvasz.dev"))).shouldBeEmpty()
            }
        }
    }
})
