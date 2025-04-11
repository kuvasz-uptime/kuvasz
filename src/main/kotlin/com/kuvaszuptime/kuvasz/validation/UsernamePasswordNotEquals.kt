package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint
import java.util.*

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class UsernamePasswordNotEquals(
    val message: String = "Admin username and password should not be equal"
)

@Factory
class UsernamePasswordValidatorFactory {

    @Singleton
    fun usernamePasswordValidator(): ConstraintValidator<UsernamePasswordNotEquals, AdminAuthConfig> =
        ConstraintValidator { adminAuthConfig, _, _ ->
            val username = adminAuthConfig?.username?.lowercase()
            val password = adminAuthConfig?.password?.lowercase()
            if (username != null && password != null) username != password else false
        }

    private fun String.lowercase() = lowercase(Locale.getDefault())
}
