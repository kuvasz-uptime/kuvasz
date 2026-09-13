package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import jakarta.validation.ValidationException

class CategoryReferencesTest : BehaviorSpec({

    given("validateCategories()") {

        `when`("there are no references at all") {
            then("the result should be empty") {
                validateCategories(emptyList()).shouldBeEmpty()
            }
        }

        `when`("a reference is surrounded by whitespace") {
            then("it should be trimmed, so that it matches the normalized category of a monitor") {
                validateCategories(listOf("  Payments  ", "\tSearch\n")) shouldBe setOf("Payments", "Search")
            }
        }

        `when`("a reference is blank") {
            then("it should be dropped instead of becoming an empty category") {
                validateCategories(listOf("Payments", "", "   ")) shouldBe setOf("Payments")
            }
        }

        `when`("the same reference is listed more than once") {
            then("it should be deduplicated") {
                validateCategories(listOf("Payments", "Payments")) shouldBe setOf("Payments")
            }
        }

        `when`("two references only differ in their surrounding whitespace") {
            then("they should collapse into one, because deduplication happens after the trimming") {
                validateCategories(listOf("Payments", " Payments")) shouldBe setOf("Payments")
            }
        }

        `when`("two references only differ in their casing") {
            then("they should be kept apart, mirroring the case-sensitive category of the monitors") {
                validateCategories(listOf("Payments", "payments")) shouldBe setOf("Payments", "payments")
            }
        }

        `when`("a reference is not in use by any monitor") {
            then("it should be kept, because a category is a predicate and not a foreign key") {
                validateCategories(listOf("Nobody uses me")) shouldBe setOf("Nobody uses me")
            }
        }

        `when`("a reference is exactly as long as the limit") {
            val category = "a".repeat(Validation.MAX_CATEGORY_LENGTH)

            then("it should be accepted") {
                validateCategories(listOf(category)) shouldBe setOf(category)
            }
        }

        `when`("a reference is longer than the limit") {
            val category = "a".repeat(Validation.MAX_CATEGORY_LENGTH + 1)

            then("it should be rejected") {
                val exception = shouldThrow<ValidationException> { validateCategories(listOf(category)) }

                exception.message shouldBe ValidationMessages.REFERENCED_CATEGORY_MAX_SIZE
            }
        }

        `when`("a reference is only longer than the limit because of its whitespace") {
            val category = "a".repeat(Validation.MAX_CATEGORY_LENGTH)

            then("it should be accepted, since the length is measured on the trimmed value") {
                validateCategories(listOf("  $category  ")) shouldBe setOf(category)
            }
        }
    }
})
