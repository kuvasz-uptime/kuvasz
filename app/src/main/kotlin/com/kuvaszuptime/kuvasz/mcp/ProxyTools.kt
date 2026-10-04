package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.schemas.ProxyListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.ProxySchema
import com.kuvaszuptime.kuvasz.services.proxy.ProxyRegistry
import io.micronaut.mcp.annotations.Tool
import jakarta.inject.Singleton

@Singleton
class ProxyTools(private val proxyRegistry: ProxyRegistry) {

    @Tool(
        name = ToolNames.LIST_PROXIES,
        description = "Lists the outbound proxies configured in the YAML config, which HTTP monitors can be " +
            "checked through by referencing their name, with their type (HTTP or SOCKS5), address, and whether " +
            "Kuvasz authenticates to them. Proxies can't be created through this server.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listProxies(): ProxyListSchema = ProxyListSchema(proxyRegistry.getProxyDtos().map { ProxySchema.fromDto(it) })
}
