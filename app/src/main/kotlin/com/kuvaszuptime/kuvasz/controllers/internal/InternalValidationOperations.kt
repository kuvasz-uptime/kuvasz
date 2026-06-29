package com.kuvaszuptime.kuvasz.controllers.internal

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue

interface InternalValidationOperations {

    /**
     * Validates a cron expression against Micronaut's [io.micronaut.scheduling.cron.CronExpression] parser, which is
     * the single source of truth for what the maintenance-window scheduling accepts. Returns 200 valid, 400 otherwise.
     */
    @Get("/cron")
    fun validateCron(@QueryValue value: String): HttpResponse<Unit>
}
