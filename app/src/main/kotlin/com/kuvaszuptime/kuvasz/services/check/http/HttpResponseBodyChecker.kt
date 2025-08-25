package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.models.ExpectedKeywordNotFoundException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.getBodyAs
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
class HttpResponseBodyChecker(
    eventDispatcher: EventDispatcher,
    uptimeEventRepository: HttpUptimeEventRepository,
) : HttpResponseChecker(eventDispatcher, uptimeEventRepository) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Checks the response body for the expected keyword.
     * If the keyword is found and not negated, it returns a [HttpCheckResult.Continue] result.
     * If the keyword is not found or negated, it dispatches a down event with the error
     * and returns a [HttpCheckResult.Finished] result.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override fun evaluate(ctx: HttpResponseCheckContext): HttpCheckResult {
        val response = ctx.response
        val monitor = ctx.monitor
        val expectedKeyword = monitor.expectedKeyword?.ifEmpty { null } ?: return HttpCheckResult.Continue

        return try {
            val responseBody = response.httpResponse.getBodyAs<String>()
                ?: return dispatchDownEvent(
                    ctx,
                    error = ExpectedKeywordNotFoundException("Response body is empty or not a string")
                )

            val containsKeyword = if (monitor.expectedKeywordCaseSensitive) {
                responseBody.contains(expectedKeyword)
            } else {
                responseBody.contains(expectedKeyword, ignoreCase = true)
            }

            if (containsKeyword != !monitor.expectedKeywordNegated) {
                logger.debug(
                    "Response body check failed for monitor with ID: ${monitor.id}, expected keyword: $expectedKeyword"
                )
                val caseSensitiveHint = if (monitor.expectedKeywordCaseSensitive) {
                    "(case-sensitive)"
                } else {
                    "(case-insensitive)"
                }
                val errorMessage = if (monitor.expectedKeywordNegated) {
                    "Response body should not contain the expected keyword: $expectedKeyword $caseSensitiveHint"
                } else {
                    "Response body does not contain the expected keyword: $expectedKeyword $caseSensitiveHint"
                }
                dispatchDownEvent(ctx, error = ExpectedKeywordNotFoundException(errorMessage))
            } else {
                logger.debug(
                    "Response body check passed for monitor with ID: ${monitor.id}, " +
                        "expected keyword: $expectedKeyword, found: $containsKeyword"
                )
                HttpCheckResult.Continue
            }
        } catch (ex: Exception) {
            val message = "Error while checking response body for monitor with ID: ${monitor.id}, " +
                "expected keyword: $expectedKeyword, error: ${ex.message}"
            logger.error(message, ex)
            dispatchDownEvent(ctx, error = ExpectedKeywordNotFoundException(message))
        }
    }
}
