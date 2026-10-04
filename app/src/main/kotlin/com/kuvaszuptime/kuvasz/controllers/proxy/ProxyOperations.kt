package com.kuvaszuptime.kuvasz.controllers.proxy

import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyDto
import io.micronaut.http.annotation.Get
import io.swagger.v3.oas.annotations.Operation

interface ProxyOperations {

    @Operation(summary = "Returns all the configured proxies")
    @Get("/")
    fun getProxies(): List<ProxyDto>
}
