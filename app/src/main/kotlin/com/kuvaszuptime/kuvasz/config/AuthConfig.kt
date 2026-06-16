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
@Requires(property = "micronaut.security.oauth2.clients.oidc.enabled", notEquals = "true")
class AdminAuthConfig {
    @field:NotBlank(message = "Admin username must not be blank")
    var username: String? = null

    @field:NotBlank(message = "Admin password must not be blank")
    @field:Size(min = 12, message = "Admin password must be at least {min} characters")
    var password: String? = null
}

@ConfigurationProperties("admin-auth.oidc")
@Context
@Introspected
@Requires(property = "micronaut.security.oauth2.clients.oidc.enabled", value = "true")
class OidcConfig {

    /**
     * Optional allowlist of email addresses that are permitted to sign in via OIDC. When empty (the default),
     * any user the OIDC provider authenticates is allowed in. Matching is case-insensitive.
     */
    var allowedEmails: List<String> = emptyList()

    /**
     * When true (the default), an email address is only considered allowed if the provider also reports it as
     * verified (the `email_verified` claim). Only relevant when [allowedEmails] is not empty.
     */
    var requireVerifiedEmail: Boolean = true

    val isEmailAllowlistEnabled: Boolean get() = normalizedAllowedEmails.isNotEmpty()

    private val normalizedAllowedEmails: Set<String>
        get() = allowedEmails.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }.toSet()

    fun isEmailAllowed(email: String?, emailVerified: Boolean?): Boolean = when {
        !isEmailAllowlistEnabled -> true
        email == null -> false
        requireVerifiedEmail && emailVerified != true -> false
        else -> email.trim().lowercase() in normalizedAllowedEmails
    }
}

@ConfigurationProperties("admin-auth")
@Context
@Introspected
@Requires(property = "micronaut.security.enabled", value = "true")
class ApiKeyConfig {

    var apiKey: String? = null

    var mcpApiKey: String? = null

    fun isApiKeyDisabled() = apiKey.isNullOrBlank()

    fun isMcpApiKeyDisabled() = mcpApiKey.isNullOrBlank()
}
