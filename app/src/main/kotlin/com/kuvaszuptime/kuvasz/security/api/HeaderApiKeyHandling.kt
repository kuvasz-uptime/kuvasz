package com.kuvaszuptime.kuvasz.security.api

import com.kuvaszuptime.kuvasz.config.ApiKeyConfig
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.controllers.MCP_PATH
import com.kuvaszuptime.kuvasz.security.Role
import com.kuvaszuptime.kuvasz.util.constantTimeEquals
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
        const val API_KEY_MIN_LENGTH = 16
    }
}

@Singleton
@Requires(property = "micronaut.security.enabled", value = "true")
class ApiKeyTokenValidator(
    private val apiKeyConfig: ApiKeyConfig,
) : TokenValidator<HttpRequest<*>?> {

    override fun validateToken(token: String, request: HttpRequest<*>?): Publisher<Authentication> {
        val path = request?.path ?: return Publishers.empty()

        return when {
            // The REST API is protected by the regular API key, granting ROLE_API
            path.startsWith(API_V2_PREFIX) && !apiKeyConfig.isApiKeyDisabled() &&
                token.constantTimeEquals(apiKeyConfig.apiKey) ->
                Publishers.just(Authentication.build("api-user", listOf(Role.API.alias)))

            // The MCP endpoint is protected by a dedicated API key, granting ROLE_MCP exclusively
            path.startsWith(MCP_PATH) && !apiKeyConfig.isMcpApiKeyDisabled() &&
                token.constantTimeEquals(apiKeyConfig.mcpApiKey) ->
                Publishers.just(Authentication.build("mcp-user", listOf(Role.MCP.alias)))

            else -> Publishers.empty()
        }
    }
}
