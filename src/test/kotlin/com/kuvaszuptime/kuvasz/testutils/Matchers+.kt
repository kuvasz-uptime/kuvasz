package com.kuvaszuptime.kuvasz.testutils

import io.kotest.matchers.booleans.shouldBeTrue
import java.time.OffsetDateTime

infix fun OffsetDateTime.shouldBe(otherOffsetDateTime: OffsetDateTime) =
    isEqual(otherOffsetDateTime).shouldBeTrue()
