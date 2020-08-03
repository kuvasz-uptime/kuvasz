package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.util.toNullable
import com.kuvaszuptime.kuvasz.util.unnest
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import org.slf4j.LoggerFactory
import java.net.URI
import javax.inject.Singleton

@Introspected
data class HttpMessageLog(
    val status: Int? = null,
    val method: String,
    val uri: URI,
    val headers: List<String>?,
    val body: Any?
)

@Singleton
@Requires(property = "app-config.http-communication-logging.enabled", value = "true")
class HttpCommunicationLogger {

    companion object {
        private val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule())
            setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        }

        private val logger = LoggerFactory.getLogger(HttpCommunicationLogger::class.java)
    }

    fun log(request: HttpRequest<*>) {
        logger.info(
            objectMapper.writeValueAsString(
                request.toLog(
                    withHeaders = logger.isDebugEnabled,
                    withBody = logger.isTraceEnabled
                )
            )
        )
    }

    fun log(request: HttpRequest<*>, response: HttpResponse<*>) {
        logger.info(
            objectMapper.writeValueAsString(
                response.toLog(
                    request,
                    withHeaders = logger.isDebugEnabled,
                    withBody = logger.isTraceEnabled
                )
            )
        )
    }

    private fun HttpRequest<*>.toLog(
        withHeaders: Boolean = false,
        withBody: Boolean = false
    ): HttpMessageLog =
        HttpMessageLog(
            method = method.name,
            uri = uri,
            headers = if (withHeaders) headers.toList() else null,
            body = if (withBody) body.unnest().toNullable() else null
        )

    private fun HttpResponse<*>.toLog(
        request: HttpRequest<*>,
        withHeaders: Boolean = false,
        withBody: Boolean = false
    ): HttpMessageLog =
        HttpMessageLog(
            status = status.code,
            method = request.method.name,
            uri = request.uri,
            headers = if (withHeaders) headers.toList() else null,
            body = if (withBody) {
                body.unnest().toNullable()?.let { nonNullBody ->
                    when (nonNullBody) {
                        is ByteBuffer<*> -> getBody(JsonNode::class.java).toNullable()
                        else -> nonNullBody
                    }
                }
            } else {
                null
            }
        )

    private fun HttpHeaders.toList() = flatMap { (key, value) -> value.map { "$key: $it" } }
}
