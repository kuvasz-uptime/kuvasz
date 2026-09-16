package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.config.ConnectivityCheckConfig
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class ValidConnectivityTargets(
    val message: String = ValidationMessages.CONNECTIVITY_CHECK_TARGET_FORMAT
)

@Factory
class ConnectivityTargetsValidatorFactory {

    @Singleton
    fun validTargetsValidator(): ConstraintValidator<ValidConnectivityTargets, ConnectivityCheckConfig> =
        ConstraintValidator { config, _, context ->
            when {
                config == null -> true

                config.targets.isEmpty() -> {
                    context.messageTemplate(ValidationMessages.CONNECTIVITY_CHECK_TARGETS_NOT_EMPTY)
                    false
                }

                !config.areTargetsValid -> {
                    context.messageTemplate(ValidationMessages.CONNECTIVITY_CHECK_TARGET_FORMAT)
                    false
                }

                else -> true
            }
        }
}
