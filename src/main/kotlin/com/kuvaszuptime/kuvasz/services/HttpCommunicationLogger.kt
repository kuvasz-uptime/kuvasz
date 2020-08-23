package com.kuvaszuptime.kuvasz.services

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.kuvaszuptime.kuvasz.config.HttpCommunicationLogConfig
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.logging.LogLevel
import org.slf4j.LoggerFactory
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Introspected
data class HttpMessageLog(
    val status: Int? = null,
    val method: String,
    val uri: URI,
    val headers: List<String>?,
    val body: String?
)

@Singleton
@Requires(property = "http-communication-log.enabled", value = "true")
class HttpCommunicationLogger @Inject constructor(httpLogConfig: HttpCommunicationLogConfig) {

    private val withHeaders = httpLogConfig.level.ordinal <= LogLevel.DEBUG.ordinal
    private val withBody = httpLogConfig.level.ordinal <= LogLevel.TRACE.ordinal

    companion object {
        private val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule())
            setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        }

        private val logger = LoggerFactory.getLogger(HttpCommunicationLogger::class.java)
    }

    fun log(request: HttpRequest<*>) {
        logger.info(objectMapper.writeValueAsString(request.toLog()))
    }

    fun log(request: HttpRequest<*>, response: HttpResponse<*>) {
        logger.info(objectMapper.writeValueAsString(response.toLog(request)))
    }

    private fun HttpRequest<*>.toLog(): HttpMessageLog =
        HttpMessageLog(
            method = method.name,
            uri = uri,
            headers = if (withHeaders) headers.toList() else null,
            body = if (withBody) getBody(String::class.java).orElse("") else null
        )

    private fun HttpResponse<*>.toLog(request: HttpRequest<*>): HttpMessageLog =
        HttpMessageLog(
            status = status.code,
            method = request.method.name,
            uri = request.uri,
            headers = if (withHeaders) headers.toList() else null,
            body = if (withBody) getBody(String::class.java).orElse("") else null
        )

    private fun HttpHeaders.toList() = flatMap { (key, value) -> value.map { "$key: $it" } }
}
