package com.kuvaszuptime.kuvasz

import com.kuvaszuptime.kuvasz.security.api.HeaderApiKeyReader
import io.micronaut.runtime.Micronaut.build
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.security.SecuritySchemes
import io.swagger.v3.oas.annotations.tags.Tag

@OpenAPIDefinition(
    info = Info(
        title = "Kuvasz Uptime",
        version = "latest",
    ),
    tags = [
        Tag(name = "Management"),
        Tag(name = "Monitors"),
        Tag(name = "HTTP monitors (V1, deprecated)"),
        Tag(name = "HTTP monitors"),
        Tag(name = "Settings (V1, deprecated)"),
        Tag(name = "Settings"),
        Tag(name = "Integrations"),
    ]
)
@SecuritySchemes(
    SecurityScheme(
        name = "apiKey",
        type = SecuritySchemeType.APIKEY,
        paramName = HeaderApiKeyReader.API_KEY_HEADER_NAME,
        `in` = SecuritySchemeIn.HEADER
    ),
    SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
    )
)
@Suppress("SpreadOperator")
object Application {
    @JvmStatic
    fun main(args: Array<String>) {
        build()
            .args(*args)
            .packages("com.kuvaszuptime.kuvasz")
            .banner(false)
            .start()
    }
}
