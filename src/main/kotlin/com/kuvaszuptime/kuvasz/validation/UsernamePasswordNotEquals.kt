package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import javax.inject.Singleton
import javax.validation.Constraint

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class UsernamePasswordNotEquals(
    val message: String = "Admin username and password should not be equal"
)

@Factory
class UsernamePasswordValidatorFactory {

    @Singleton
    fun usernamePasswordValidator(): ConstraintValidator<UsernamePasswordNotEquals, AdminAuthConfig> {
        return ConstraintValidator { adminAuthConfig, _, _ ->
            if (adminAuthConfig != null) {
                adminAuthConfig.username!!.toLowerCase() != adminAuthConfig.password!!.toLowerCase()
            } else true
        }
    }
}
