package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostAuthMethod
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema

@JsonSchema
@Introspected
data class DockerHostListSchema(val dockerHosts: List<DockerHostSchema>)

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DockerHostSchema(
    val name: String,
    val url: String,
    val tlsEnabled: Boolean,
    val authMethod: DockerHostAuthMethod,
    val apiVersion: String?,
) {
    companion object {
        fun fromDto(dto: DockerHostDto) = DockerHostSchema(
            name = dto.name,
            url = dto.url,
            tlsEnabled = dto.tlsEnabled,
            authMethod = dto.authMethod,
            apiVersion = dto.apiVersion,
        )
    }
}
