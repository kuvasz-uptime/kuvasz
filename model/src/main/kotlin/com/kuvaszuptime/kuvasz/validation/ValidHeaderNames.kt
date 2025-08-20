package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class ValidHeaderNames(
    val message: String = ValidationMessages.VALID_HEADER_NAMES,
)

@Factory
class HttpHeaderMapValidatorFactory {

    private val headerPattern = Regex("^[a-zA-Z][a-zA-Z0-9-]*$")

    @Singleton
    fun headerMapValidator(): ConstraintValidator<ValidHeaderNames, Map<String, String>> =
        ConstraintValidator { headers, _, _ ->
            if (headers.isNullOrEmpty()) return@ConstraintValidator true
            headers.all { header ->
                header.key.matches(headerPattern)
            }
        }
}
