package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.dto.MaintenanceWindowValidationMessages
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint
import org.slf4j.LoggerFactory
import java.time.Duration

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class ValidDuration(
    val message: String = MaintenanceWindowValidationMessages.DURATION_INVALID,
)

private val durationLogger = LoggerFactory.getLogger("com.kuvaszuptime.kuvasz.validation.DurationValidation")

/**
 * Whether the given expression is a valid, strictly positive ISO-8601 duration. Single source of truth used both by
 * the [ValidDuration] bean-validation constraint and by the explicit schedule validation that guards every write path.
 */
fun isValidDuration(expression: String): Boolean =
    try {
        // Only positive durations make sense for a maintenance window
        !Duration.parse(expression).run { isZero || isNegative }
    } catch (ex: java.time.format.DateTimeParseException) {
        durationLogger.debug("Invalid ISO-8601 duration: $expression", ex)
        false
    }

@Factory
class DurationValidatorFactory {

    @Singleton
    fun durationValidator(): ConstraintValidator<ValidDuration, String?> =
        ConstraintValidator { input, _, _ ->
            input.isNullOrBlank() || isValidDuration(input)
        }
}
