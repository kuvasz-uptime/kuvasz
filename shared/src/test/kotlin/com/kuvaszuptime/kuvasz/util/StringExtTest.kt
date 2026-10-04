package com.kuvaszuptime.kuvasz.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe

class StringExtTest : StringSpec({

    "nullIfBlank should trim the string, and turn it into null if nothing is left" {
        table(
            headers("input", "expected"),
            row(null, null),
            row("", null),
            row("   ", null),
            row("\t\n", null),
            row("value", "value"),
            row(" value ", "value"),
            row("\tsome value\n", "some value"),
        ).forAll { input, expected ->
            input.nullIfBlank() shouldBe expected
        }
    }
})
