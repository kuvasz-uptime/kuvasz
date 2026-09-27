package com.kuvaszuptime.kuvasz.controllers.docker

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostDto
import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("${API_V2_PREFIX}/docker-hosts", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.DOCKER_HOSTS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class DockerHostController(
    private val dockerHostRegistry: DockerHostRegistry?,
    private val dockerApiClient: DockerApiClient?,
) : DockerHostOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "The list of configured Docker hosts",
        ),
    )
    override fun getDockerHosts(): List<DockerHostDto> = dockerHostRegistry?.getHostDtos(dockerApiClient).orEmpty()
}
