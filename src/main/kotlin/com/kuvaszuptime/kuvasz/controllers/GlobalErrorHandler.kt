package com.kuvaszuptime.kuvasz.controllers

import com.fasterxml.jackson.core.JsonParseException
import com.kuvaszuptime.kuvasz.models.DuplicationError
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundError
import com.kuvaszuptime.kuvasz.models.PersistenceError
import com.kuvaszuptime.kuvasz.models.SchedulingError
import com.kuvaszuptime.kuvasz.models.ServiceError
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import javax.validation.ValidationException

@Controller
class GlobalErrorHandler {

    @Suppress("UNUSED_PARAMETER")
    @Error(global = true)
    fun jsonError(request: HttpRequest<*>, monitorNotFoundError: MonitorNotFoundError): HttpResponse<ServiceError> {
        val error = ServiceError(monitorNotFoundError.message)
        return HttpResponse.notFound(error)
    }

    @Suppress("UNUSED_PARAMETER")
    @Error(global = true)
    fun jsonError(request: HttpRequest<*>, duplicationError: DuplicationError): HttpResponse<ServiceError> {
        val error = ServiceError(duplicationError.message)
        return HttpResponse.status<ServiceError>(HttpStatus.CONFLICT).body(error)
    }

    @Suppress("UNUSED_PARAMETER")
    @Error(global = true)
    fun error(request: HttpRequest<*>, throwable: ValidationException): HttpResponse<ServiceError> =
        HttpResponse.badRequest(ServiceError(throwable.message))

    @Suppress("UNUSED_PARAMETER")
    @Error(global = true)
    fun error(request: HttpRequest<*>, throwable: ConversionErrorException): HttpResponse<ServiceError> {
        val message = "Failed to convert argument: ${throwable.argument}"
        return HttpResponse.badRequest(ServiceError(message))
    }

    @Suppress("UNUSED_PARAMETER")
    @Error(global = true)
    fun error(request: HttpRequest<*>, throwable: JsonParseException): HttpResponse<ServiceError> {
        val message = "Can't parse the JSON in the payload"
        return HttpResponse.badRequest(ServiceError(message))
    }

    @Suppress("UNUSED_PARAMETER")
    @Error(global = true)
    fun jsonError(request: HttpRequest<*>, persistencyError: PersistenceError): HttpResponse<ServiceError> {
        val error = ServiceError(persistencyError.message)
        return HttpResponse.status<ServiceError>(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }

    @Suppress("UNUSED_PARAMETER")
    @Error(global = true)
    fun jsonError(request: HttpRequest<*>, schedulingError: SchedulingError): HttpResponse<ServiceError> {
        val error = ServiceError(schedulingError.message)
        return HttpResponse.status<ServiceError>(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }
}
