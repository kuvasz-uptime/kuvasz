@file:Suppress("MaxLineLength")

package com.kuvaszuptime.kuvasz

import com.kuvaszuptime.kuvasz.security.api.HeaderApiKeyReader
import io.micronaut.runtime.Micronaut.build
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.extensions.Extension
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.security.SecuritySchemes
import io.swagger.v3.oas.annotations.tags.Tag

@OpenAPIDefinition(
    info = Info(
        title = "Kuvasz Uptime",
        version = "latest",
        description = "Kuvasz Uptime is an open-source, self-hostable uptime monitoring and status page service.",
        contact = Contact(name = "Official Documentation", url = "https://kuvasz-uptime.dev"),
        extensions = [
            Extension(
                name = "x-logo",
                properties = [
                    ExtensionProperty(
                        name = "url",
                        value = "https://github.com/kuvasz-uptime/kuvasz/raw/main/docs/docs/images/kuvasz-banner-light.webp",
                    ),
                ],
            ),
        ]
    ),
    tags = [
        Tag(name = "Management"),
        Tag(name = "Monitors"),
        Tag(name = "HTTP monitors (V1, deprecated)"),
        Tag(name = "HTTP monitors"),
        Tag(name = "Settings (V1, deprecated)"),
        Tag(name = "Settings"),
        Tag(name = "Integrations"),
        Tag(name = "Status pages"),
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
