package com.kuvaszuptime.kuvasz.services.docker

import com.kuvaszuptime.kuvasz.config.DockerHostConfig
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostDto
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import jakarta.annotation.PostConstruct

@Context
@Requires(bean = DockerHostConfig::class)
class DockerHostRegistry(private val hostConfigs: List<DockerHostConfig>) {

    companion object {
        private val logger = loggerFor<DockerHostRegistry>()
    }

    val configuredHosts: Map<String, DockerHost> by lazy {
        val result = mutableMapOf<String, DockerHost>()
        hostConfigs.forEach { config ->
            val name = config.name.trim()
            if (name in result) {
                throw DockerHostConfigException(
                    "Duplicate Docker host configuration found for [$name]. " +
                        "Please ensure each Docker host has a unique name."
                )
            }
            result[name] = config.resolve(name)
        }
        result.toMap()
    }

    operator fun get(name: String): DockerHost? = configuredHosts[name]

    fun getConfiguredHostDtos(): List<DockerHostDto> = configuredHosts.values.map { host ->
        DockerHostDto(name = host.name, url = host.url, tlsEnabled = host.tlsEnabled)
    }

    @PostConstruct
    fun init() {
        configuredHosts.values
            .joinToString(", ") { "${it.name} -> ${it.url}" }
            .let { logger.info("Configured Docker hosts: [$it]") }
    }

    private fun DockerHostConfig.resolve(name: String): DockerHost = try {
        val tlsMaterial = DockerDaemonUrl.resolveTlsMaterial(ca = tls?.ca, cert = tls?.cert, key = tls?.key)
        DockerHost(name = name, url = url.trim(), address = DockerDaemonUrl.parse(url, tlsMaterial))
    } catch (ex: DockerHostConfigException) {
        throw DockerHostConfigException("Invalid configuration for Docker host [$name]: ${ex.message}", ex)
    }
}
