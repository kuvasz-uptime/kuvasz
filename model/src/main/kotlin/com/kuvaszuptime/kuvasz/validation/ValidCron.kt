package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.dto.MaintenanceWindowValidationMessages
import io.micronaut.context.annotation.Factory
import io.micronaut.scheduling.cron.CronExpression
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class ValidCron(
    val message: String = MaintenanceWindowValidationMessages.CRON_INVALID,
)

/**
 * Whether the given expression is a valid (Micronaut) cron expression. Single source of truth used both by the
 * [ValidCron] bean-validation constraint and by the explicit schedule validation that guards every write path.
 */
fun isValidCron(expression: String): Boolean =
    try {
        CronExpression.create(expression)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

@Factory
class CronValidatorFactory {

    @Singleton
    fun cronValidator(): ConstraintValidator<ValidCron, String?> =
        ConstraintValidator { input, _, _ ->
            input.isNullOrBlank() || isValidCron(input)
        }
}
