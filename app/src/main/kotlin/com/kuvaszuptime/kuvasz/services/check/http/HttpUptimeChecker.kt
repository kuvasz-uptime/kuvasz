package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.util.isServerRelatedError
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.HttpException
import io.micronaut.retry.annotation.Retryable
import io.micronaut.runtime.ApplicationConfiguration
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.util.Optional

@Singleton
class HttpUptimeChecker(
    @param:Client(configuration = HttpCheckerClientConfiguration::class)
    private val httpClient: HttpClient,
    private val monitorRepository: HttpMonitorRepository,
    private val checkRequestConfigurator: HttpCheckRequestConfigurator,
    private val checkResponseEvaluator: HttpCheckResponseEvaluator,
) {

    companion object {
        private const val RETRY_COUNT = 2L
        private const val RETRY_INITIAL_DELAY = "500ms"
        private const val RETRY_BACKOFF_MULTIPLIER = 3L
        private val logger = LoggerFactory.getLogger(HttpUptimeChecker::class.java)
    }

    suspend fun check(
        monitor: HttpMonitorRecord,
        uriOverride: URI? = null,
        visitedUrls: MutableList<URI> = mutableListOf(),
        doAfter: ((monitor: HttpMonitorRecord) -> Unit)? = null,
    ) {
        if (uriOverride == null) {
            logger.debug("Starting uptime check for monitor (${monitor.name}) on URL: ${monitor.url}")
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            val effectiveUrl = uriOverride ?: URI(monitor.url)
            visitedUrls.add(effectiveUrl)

            val checkResponse = sendHttpRequest(monitor, uri = effectiveUrl)
            when (val result = checkResponseEvaluator.evaluateResponse(monitor, checkResponse, visitedUrls)) {
                is HttpCheckResult.Redirected -> check(monitor, result.redirectionUri, result.visitedUrls)
                HttpCheckResult.Continue -> {
                    logger.warn("HTTP uptime check for monitor with ID: ${monitor.id} returned Continue unexpectedly")
                }

                HttpCheckResult.Finished -> {
                    logger.debug("HTTP uptime check for monitor with ID: ${monitor.id} finished successfully")
                }
            }
        } catch (error: Exception) {
            checkResponseEvaluator.evaluateError(monitor, error)
        }
        logger.debug("Uptime check for monitor (${monitor.name}) finished")
        if (doAfter != null) {
            monitorRepository.findById(monitor.id, null)?.let { upToDateMonitor ->
                logger.debug("Calling doAfter() hook on monitor with name [${upToDateMonitor.name}]")
                doAfter(upToDateMonitor)
            }
        }
    }

    @Retryable(
        delay = RETRY_INITIAL_DELAY,
        attempts = "$RETRY_COUNT",
        multiplier = "$RETRY_BACKOFF_MULTIPLIER",
        includes = [HttpException::class],
    )
    suspend fun sendHttpRequest(monitor: HttpMonitorRecord, uri: URI): HttpCheckResponse {
        logger.debug("Sending HTTP request to $uri (${monitor.name})")
        val request = checkRequestConfigurator.fromMonitor(monitor, uri)
        val start = System.currentTimeMillis()
        val httpResponse = httpClient.exchange(
            request,
            Argument.of(ByteBuffer::class.java),
            Argument.of(ByteBuffer::class.java),
        ).awaitSingle()
        val latency = (System.currentTimeMillis() - start).toInt()
        if (httpResponse.isServerRelatedError()) {
            throw HttpClientResponseException(httpResponse.status.reason, httpResponse)
        }

        return HttpCheckResponse(
            httpResponse = httpResponse,
            latency = latency
        )
    }
}

@Singleton
class HttpCheckerClientConfiguration(config: ApplicationConfiguration) : HttpClientConfiguration(config) {

    override fun getEventLoopGroup(): String = EVENT_LOOP_GROUP

    override fun isFollowRedirects(): Boolean = false

    override fun getReadTimeout(): Optional<Duration> = Optional.of(Duration.ofSeconds(READ_TIMEOUT_SECONDS))

    override fun getConnectionPoolConfiguration(): ConnectionPoolConfiguration = ConnectionPoolConfiguration()

    override fun isExceptionOnErrorStatus(): Boolean = false

    override fun getMaxHeaderSize(): Int = MAX_HEADER_SIZE

    companion object {
        private const val EVENT_LOOP_GROUP = "uptime-check"
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val MAX_HEADER_SIZE = 16384
    }
}
