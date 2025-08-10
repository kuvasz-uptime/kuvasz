package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.validation.UsernamePasswordNotEquals
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@ConfigurationProperties("admin-auth")
@UsernamePasswordNotEquals
@Context
@Introspected
@Requires(property = "micronaut.security.enabled", value = "true")
class AdminAuthConfig {
    @field:NotBlank(message = "Admin username must not be blank")
    var username: String? = null

    @field:NotBlank(message = "Admin password must not be blank")
    @field:Size(min = 12, message = "Admin password must be at least {min} characters")
    var password: String? = null

    @field:NotBlank(message = "Admin API key must not be blank")
    @field:Size(min = 16, message = "Admin API key must be at least {min} characters")
    var apiKey: String? = null
}
