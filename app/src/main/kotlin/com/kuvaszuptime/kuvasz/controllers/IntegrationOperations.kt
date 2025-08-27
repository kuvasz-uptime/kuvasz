package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.IntegrationConfigDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.services.integrations.NotificationTestResult
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.reactivex.rxjava3.core.Single
import io.swagger.v3.oas.annotations.Operation

interface IntegrationOperations {

    @Operation(summary = "Send a test notification with the specified integration")
    @Post("/{integrationId}/test")
    fun sendTestNotification(
        @PathVariable integrationId: IntegrationID
    ): Single<NotificationTestResult>

    @Operation(summary = "Returns all the configured integrations")
    @Get("/")
    fun getIntegrations(): List<IntegrationConfigDto>
}
