package com.kuvaszuptime.kuvasz.controllers.proxy

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyDto
import com.kuvaszuptime.kuvasz.services.proxy.ProxyRegistry
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("${API_V2_PREFIX}/proxies", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.PROXIES)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class ProxyController(private val proxyRegistry: ProxyRegistry?) : ProxyOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "The list of configured proxies",
        ),
    )
    override fun getProxies(): List<ProxyDto> = proxyRegistry?.getProxyDtos().orEmpty()
}
