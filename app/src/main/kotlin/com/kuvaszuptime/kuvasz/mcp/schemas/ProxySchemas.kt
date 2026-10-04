package com.kuvaszuptime.kuvasz.mcp.schemas

import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyDto
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema

@JsonSchema
@Introspected
data class ProxyListSchema(val proxies: List<ProxySchema>)

@Introspected
data class ProxySchema(
    val name: String,
    val type: ProxyType,
    val host: String,
    val port: Int,
    val authenticated: Boolean,
) {
    companion object {
        fun fromDto(dto: ProxyDto) = ProxySchema(
            name = dto.name,
            type = dto.type,
            host = dto.host,
            port = dto.port,
            authenticated = dto.authenticated,
        )
    }
}
