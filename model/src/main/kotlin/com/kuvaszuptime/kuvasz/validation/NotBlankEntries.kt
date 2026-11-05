package com.kuvaszuptime.kuvasz.validation

import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint

/**
 * The annotated list must contain at least one element, and none of its elements can be blank.
 */
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class NotBlankEntries(
    val message: String,
)

@Factory
class NotBlankEntriesValidatorFactory {

    @Singleton
    fun notBlankEntriesValidator(): ConstraintValidator<NotBlankEntries, List<String>> =
        ConstraintValidator { entries, _, _ ->
            !entries.isNullOrEmpty() && entries.none { it.isBlank() }
        }
}
