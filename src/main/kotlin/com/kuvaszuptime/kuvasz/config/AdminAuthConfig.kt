package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.validation.UsernamePasswordNotEquals
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@ConfigurationProperties("admin-auth")
@UsernamePasswordNotEquals
@Context
@Introspected
class AdminAuthConfig {
    @NotBlank
    var username: String? = null

    @NotBlank
    @Size(min = 12)
    var password: String? = null
}
