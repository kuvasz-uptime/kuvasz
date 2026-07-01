package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.jacksonObjectMapper

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class WellFormedJsonString(
    val message: String = ValidationMessages.WELL_FORMED_JSON_STRING,
)

@Factory
class JsonStringValidatorFactory {

    private val objectMapper = jacksonObjectMapper()

    @Singleton
    fun jsonStringValidator(): ConstraintValidator<WellFormedJsonString, String?> =
        ConstraintValidator { input, _, _ ->
            if (input.isNullOrBlank()) return@ConstraintValidator true
            @Suppress("SwallowedException")
            try {
                objectMapper.readTree(input)
                true
            } catch (_: JacksonException) {
                false
            }
        }
}
