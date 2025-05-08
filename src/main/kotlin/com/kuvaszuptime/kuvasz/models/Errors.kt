package com.kuvaszuptime.kuvasz.models

import io.micronaut.core.annotation.Introspected

@Introspected
data class ServiceError(
    val message: String? = "Something bad happened :("
)

class MonitorNotFoundException(
    private val monitorId: Long,
    override val message: String? = "There is no monitor with ID: $monitorId"
) : Exception()

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
    override val message: String? = "Invalid redirection without a Location header"
) : UptimeCheckException()

class UnknownUptimeCheckException(
    override val message: String?,
) : UptimeCheckException()
