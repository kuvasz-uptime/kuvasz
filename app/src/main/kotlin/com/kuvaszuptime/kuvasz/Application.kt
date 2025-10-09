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
        Tag(name = OpenApiTags.HTTP_MONITORS),
        Tag(name = OpenApiTags.HTTP_MONITORS_V1),
        Tag(name = OpenApiTags.INCIDENTS),
        Tag(name = OpenApiTags.INTEGRATIONS),
        Tag(name = OpenApiTags.MANAGEMENT),
        Tag(name = OpenApiTags.MONITORS),
        Tag(name = OpenApiTags.PUSH_MONITORS),
        Tag(name = OpenApiTags.SETTINGS),
        Tag(name = OpenApiTags.SETTINGS_V1),
        Tag(name = OpenApiTags.STATUS_PAGES),
    ]
)
@SecuritySchemes(
    SecurityScheme(
        name = OpenApiSecuritySchemes.API_KEY,
        type = SecuritySchemeType.APIKEY,
        paramName = HeaderApiKeyReader.API_KEY_HEADER_NAME,
        `in` = SecuritySchemeIn.HEADER
    ),
    SecurityScheme(
        name = OpenApiSecuritySchemes.BEARER_AUTH,
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
