package com.kuvaszuptime.kuvasz.models

import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.shouldBe
import jakarta.validation.ConstraintViolation

fun Set<ConstraintViolation<*>>.shouldHaveSingleError(
    propertyPath: String,
    message: String,
) {
    this.size shouldBe 1
    this.shouldHaveError(propertyPath, message)
}

fun Set<ConstraintViolation<*>>.shouldHaveError(
    propertyPath: String,
    message: String,
) {
    this.forAtLeastOne { error ->
        error.propertyPath.toString() shouldBe propertyPath
        error.message shouldBe message
    }
}
