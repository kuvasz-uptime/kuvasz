package com.kuvaszuptime.kuvasz

import com.kuvaszuptime.kuvasz.security.api.HeaderApiKeyReader
import io.micronaut.runtime.Micronaut.build
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.security.SecuritySchemes
import io.swagger.v3.oas.annotations.tags.Tag

@OpenAPIDefinition(
    info = Info(
        title = "kuvasz",
        version = "latest",
        description = "Kuvasz [pronounce as 'koovas'] is an open-source uptime and SSL monitoring service",
        contact = Contact(
            url = "https://github.com/kuvasz-uptime/kuvasz"
        )
    ),
    tags = [
        Tag(name = "Management operations"),
        Tag(name = "Monitor operations")
    ]
)
@SecuritySchemes(
    SecurityScheme(
        name = "apiKey",
        type = SecuritySchemeType.APIKEY,
        paramName = HeaderApiKeyReader.API_KEY_HEADER_NAME,
        `in` = SecuritySchemeIn.HEADER
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
