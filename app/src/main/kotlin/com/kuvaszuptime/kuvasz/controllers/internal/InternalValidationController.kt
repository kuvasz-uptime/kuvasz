package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.controllers.API_INTERNAL_PREFIX
import com.kuvaszuptime.kuvasz.validation.isValidCron
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.swagger.v3.oas.annotations.Hidden

/**
 * Undocumented helper endpoints used by the web UI for server-side validation. Hidden from the public API docs and
 * protected by the same ROLE_API rule (granted by the web login too) as the rest of the API endpoints.
 */
@Controller("$API_INTERNAL_PREFIX/validation")
@Hidden
class InternalValidationController : InternalValidationOperations {

    override fun validateCron(value: String): HttpResponse<Unit> =
        if (isValidCron(value)) HttpResponse.ok() else HttpResponse.badRequest()
}
