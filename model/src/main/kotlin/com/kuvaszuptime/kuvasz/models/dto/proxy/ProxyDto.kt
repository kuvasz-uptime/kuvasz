package com.kuvaszuptime.kuvasz.models.dto.proxy

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
@Schema(name = "Proxy", description = "An outbound proxy monitors can be checked through, configured via YAML")
data class ProxyDto(
    @param:Schema(description = "The unique name of the proxy, referenced by monitors", example = "corporate-egress")
    val name: String,
    @param:Schema(description = "The protocol of the proxy")
    val type: ProxyType,
    @param:Schema(description = "The host name or IP address of the proxy", example = "10.0.0.10")
    val host: String,
    @param:Schema(description = "The port of the proxy", example = "3128")
    val port: Int,
    @param:Schema(description = "Whether Kuvasz authenticates to the proxy with a username and password")
    val authenticated: Boolean,
)
