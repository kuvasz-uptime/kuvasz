package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.dto.MaintenanceWindowValidationMessages
import io.micronaut.context.annotation.Factory
import io.micronaut.scheduling.cron.CronExpression
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint
import org.slf4j.LoggerFactory

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class ValidCron(
    val message: String = MaintenanceWindowValidationMessages.CRON_INVALID,
)

private val cronLogger = LoggerFactory.getLogger("com.kuvaszuptime.kuvasz.validation.CronValidation")

/**
 * Whether the given expression is a valid (Micronaut) cron expression. Single source of truth used both by the
 * [ValidCron] bean-validation constraint and by the explicit schedule validation that guards every write path.
 */
fun isValidCron(expression: String): Boolean =
    try {
        CronExpression.create(expression)
        true
    } catch (ex: IllegalArgumentException) {
        cronLogger.debug("Invalid cron expression: $expression", ex)
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
