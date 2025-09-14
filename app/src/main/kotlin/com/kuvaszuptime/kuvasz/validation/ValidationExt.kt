package com.kuvaszuptime.kuvasz.validation

import jakarta.validation.ConstraintViolation
import jakarta.validation.ValidationException

fun Set<ConstraintViolation<*>>.throwIfNotEmpty() {
    if (this.isNotEmpty()) {
        throw ValidationException("Validation failed: ${joinToString { "${it.propertyPath}: ${it.message}" }}")
    }
}
