package com.kuvaszuptime.kuvasz.models

import io.micronaut.core.annotation.Introspected

@Introspected
data class ServiceError(
    val message: String? = "Something bad happened :(",
    val errorCode: ApiErrorCode? = null
)

sealed class ResourceNotFoundException : Exception()

sealed class MonitorNotFoundException(
    val monitorType: MonitorType,
    val monitorId: Long,
    override val message: String? = "There is no ${monitorType.identifier.uppercase()} monitor with ID: $monitorId",
) : ResourceNotFoundException()

class HttpMonitorNotFoundException(monitorId: Long) : MonitorNotFoundException(MonitorType.HTTP_SSL, monitorId)

open class PersistenceException(
    override val message: String? = "Something bad happened in the database :("
) : Exception()

open class DuplicationException(
    override val message: String? = "The given resource already exists"
) : PersistenceException()

class MonitorDuplicatedException(
    override val message: String? = "There is already a monitor with the given name"
) : DuplicationException()

class SchedulingException(
    override val message: String? = "Scheduling checks for the monitor did not succeed"
) : Exception()

sealed class UptimeCheckException : Exception()

class RedirectLoopException(
    override val message: String? = "Redirect loop detected"
) : UptimeCheckException()

class InvalidRedirectionException(
    override val message: String
) : UptimeCheckException()

class IneligibleStatusCodeException(
    val statusCode: Int,
    override val message: String? = "Response status code [$statusCode] was unexpected"
) : UptimeCheckException()

class ResponseTimeThresholdExceededException(
    val responseTimeMillis: Int,
    val thresholdMillis: Int,
    override val message: String? =
        "Response time exceeded the threshold of $thresholdMillis ms (actual: $responseTimeMillis ms)"
) : UptimeCheckException()

class ExpectedKeywordNotFoundException(override val message: String) : UptimeCheckException()

class ExpectedHeaderNotFoundException(
    val failingHeaders: List<String>,
    override val message: String? = "Response headers did not match the expected headers: $failingHeaders"
) : UptimeCheckException()

class StatusPageDuplicatedException(
    override val message: String? = "There is already a status page with the given slug"
) : DuplicationException()

class StatusPageNotFoundException(
    val statusPageId: Long,
    override val message: String? = "There is no status page with ID: $statusPageId",
) : ResourceNotFoundException()

sealed class ReadOnlyResourceException(override val message: String) : Exception()

class ReadOnlyMonitorException : ReadOnlyResourceException(
    "The monitors were configured via a YAML file. " +
        "You cannot modify them via the API. Please change the configuration in the YAML file and restart the server."
)

class ReadOnlyStatusPageException : ReadOnlyResourceException(
    "The status pages were configured via a YAML file. " +
        "You cannot modify them via the API. Please change the configuration in the YAML file and restart the server."
)

class ReadOnlyMonitorNameException : RuntimeException(
    "The monitor's name cannot be changed, because it's already referenced in the YAML file by a status page."
)

class MonitorCannotBeDeletedException(override val message: String) : RuntimeException(message)
