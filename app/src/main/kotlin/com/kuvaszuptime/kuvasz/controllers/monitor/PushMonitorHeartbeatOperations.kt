package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.models.dto.monitor.push.heartbeat.PushMonitorFailureDetailsDto
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.swagger.v3.oas.annotations.Operation

interface PushMonitorHeartbeatOperations {

    @Operation(summary = "Send a push monitor heartbeat")
    @Post("/{clientSecret}")
    fun sendHeartbeatViaPost(@PathVariable clientSecret: String)

    @Operation(summary = "Send a push monitor heartbeat")
    @Get("/{clientSecret}")
    fun sendHeartbeatViaGet(@PathVariable clientSecret: String)

    @Operation(summary = "Signal a push monitor failure")
    @Post("/{clientSecret}/failure")
    fun signalFailureViaPost(@PathVariable clientSecret: String, @Body failureDetails: PushMonitorFailureDetailsDto?)

    @Operation(summary = "Signal a push monitor failure")
    @Get("/{clientSecret}/failure")
    fun signalFailureViaGet(@PathVariable clientSecret: String)
}
