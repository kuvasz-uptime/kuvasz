package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.checks.SupportedExpectedHttpStatusCodes
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class SupportedStatusCodes(
    val message: String = MonitorValidationMessages.SUPPORTED_STATUS_CODES,
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
