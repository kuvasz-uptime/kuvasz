package com.kuvaszuptime.kuvasz.validation

import io.micronaut.validation.validator.Validator
import jakarta.validation.ConstraintViolation
import jakarta.validation.ValidationException

fun Set<ConstraintViolation<*>>.throwIfNotEmpty() {
    if (this.isNotEmpty()) {
        throw ValidationException("Validation failed: ${joinToString { "${it.propertyPath}: ${it.message}" }}")
    }
}

fun <T : Any> Validator.validated(target: T): T {
    validate(target).throwIfNotEmpty()
    return target
}
