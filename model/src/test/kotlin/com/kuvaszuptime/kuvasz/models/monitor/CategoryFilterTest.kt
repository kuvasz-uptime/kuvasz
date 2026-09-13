package com.kuvaszuptime.kuvasz.models.monitor

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CategoryFilterTest : BehaviorSpec({

    given("CategoryFilter.fromQueryParam()") {

        `when`("the parameter is not present at all") {
            then("there is no filtering, so every monitor of the type is listed") {
                CategoryFilter.fromQueryParam(null).shouldBeNull()
            }
        }

        `when`("the parameter is present but empty") {
            then("it selects the monitors that have no category") {
                CategoryFilter.fromQueryParam("") shouldBe CategoryFilter.Uncategorized
            }
        }

        `when`("the parameter is present but only whitespace") {
            then("it is taken as empty rather than as a category made of spaces") {
                CategoryFilter.fromQueryParam("   ") shouldBe CategoryFilter.Uncategorized
            }
        }

        `when`("the parameter carries a category") {
            then("it selects that one") {
                CategoryFilter.fromQueryParam("Payments") shouldBe CategoryFilter.InCategory("Payments")
            }
        }

        `when`("the category arrives with surrounding whitespace") {
            then("it is trimmed, so it matches the persisted, normalized category of a monitor") {
                CategoryFilter.fromQueryParam("  Payments  ") shouldBe CategoryFilter.InCategory("Payments")
            }
        }

        `when`("two categories only differ in their casing") {
            then("they stay apart, mirroring the case-sensitive category of the monitors") {
                CategoryFilter.fromQueryParam("payments") shouldBe CategoryFilter.InCategory("payments")
            }
        }
    }
})
