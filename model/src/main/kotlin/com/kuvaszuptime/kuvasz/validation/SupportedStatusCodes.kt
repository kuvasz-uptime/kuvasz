package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.checks.SupportedExpectedHttpStatusCodes
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class SupportedStatusCodes(
    val message: String = "All status code needs to be a valid HTTP status code between 100 and 499",
)

@Factory
class StatusCodeListValidatorFactory {

    @Singleton
    fun statusCodesValidator(): ConstraintValidator<SupportedStatusCodes, List<Int>> =
        ConstraintValidator { statusCodes, _, _ ->
            if (statusCodes.isNullOrEmpty()) return@ConstraintValidator true
            val supportedCodes = SupportedExpectedHttpStatusCodes.allCodes.map { it.code }
            supportedCodes.containsAll(statusCodes)
        }
}
