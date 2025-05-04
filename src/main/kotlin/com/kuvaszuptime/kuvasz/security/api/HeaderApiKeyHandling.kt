package com.kuvaszuptime.kuvasz.security.api

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import com.kuvaszuptime.kuvasz.controllers.API_V1_PREFIX
import com.kuvaszuptime.kuvasz.security.Role
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.reader.HttpHeaderTokenReader
import io.micronaut.security.token.validator.TokenValidator
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

@Singleton
class HeaderApiKeyReader : HttpHeaderTokenReader() {
    override fun getPrefix(): String? = null

    override fun getHeaderName(): String = API_KEY_HEADER_NAME

    companion object {
        const val API_KEY_HEADER_NAME = "X-API-KEY"
    }
}

@Singleton
@Requires(property = "micronaut.security.enabled", value = "true")
class HeaderApiKeyTokenValidator(
    private val adminAuthConfig: AdminAuthConfig,
) : TokenValidator<HttpRequest<*>?> {

    override fun validateToken(token: String, request: HttpRequest<*>?): Publisher<Authentication> =
        if (request != null && request.path.startsWith(API_V1_PREFIX) && token == adminAuthConfig.apiKey) {
            Publishers.just(Authentication.build(adminAuthConfig.username, listOf(Role.API.alias)))
        } else {
            Publishers.empty()
        }
}
