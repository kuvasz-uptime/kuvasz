package com.kuvaszuptime.kuvasz.services.proxy

import com.kuvaszuptime.kuvasz.config.ProxyConfig
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyDto
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import jakarta.annotation.PostConstruct
import jakarta.validation.ValidationException

@Context
class ProxyRegistry(private val proxyConfigs: List<ProxyConfig>) {

    companion object {
        private val logger = loggerFor<ProxyRegistry>()
    }

    val configuredProxies: Map<String, ConfiguredProxy> by lazy {
        val result = mutableMapOf<String, ConfiguredProxy>()
        proxyConfigs.forEach { config ->
            val name = config.name.trim()
            if (name in result) {
                throw ProxyConfigException(
                    "Duplicate proxy configuration found for [$name]. Please ensure each proxy has a unique name."
                )
            }
            result[name] = config.resolve(name)
        }
        result.toMap()
    }

    operator fun get(name: String): ConfiguredProxy? = configuredProxies[name]

    fun getProxyDtos(): List<ProxyDto> =
        configuredProxies.values.map { proxy ->
            ProxyDto(
                name = proxy.name,
                type = proxy.type,
                host = proxy.host,
                port = proxy.port,
                authenticated = proxy.credentials != null,
            )
        }.sortedBy { it.name }

    @PostConstruct
    fun init() {
        if (configuredProxies.isEmpty()) return
        configuredProxies.values
            .joinToString(", ") { "${it.name} -> ${it.type} ${it.host}:${it.port}" }
            .let { logger.info("Configured proxies: [$it]") }
    }

    private fun ProxyConfig.resolve(name: String): ConfiguredProxy = try {
        val (type, host, port) = ProxyUrl.parse(url)
        ConfiguredProxy(
            name = name,
            type = type,
            host = host,
            port = port,
            credentials = ProxyUrl.resolveCredentials(type, username, password),
        )
    } catch (ex: ProxyConfigException) {
        throw ProxyConfigException("Invalid configuration for proxy [$name]: ${ex.message}", ex)
    }

    fun requireConfiguredProxy(name: String): String {
        if (this[name] == null) throw NonExistingProxyException(name)
        return name
    }
}

class NonExistingProxyException(name: String) : ValidationException("Non-existing proxy found: $name.")
