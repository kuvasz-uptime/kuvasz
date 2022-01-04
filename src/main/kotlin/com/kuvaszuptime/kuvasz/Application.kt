package com.kuvaszuptime.kuvasz

import io.micronaut.runtime.Micronaut.build
import io.swagger.v3.oas.annotations.OpenAPIDefinition
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
        description = "Kuvasz [pronounce as 'koovas'] is an open-source, headless uptime and SSL monitoring service, " +
            "built in Kotlin on top of the awesome Micronaut framework",
        contact = Contact(
            url = "https://github.com/kuvasz-uptime/kuvasz"
        )
    ),
    tags = [
        Tag(name = "Security operations"),
        Tag(name = "Management operations"),
        Tag(name = "Monitor operations")
    ]
)
@SecuritySchemes(
    SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
    )
)
@Suppress("SpreadOperator")
object Application {
    @JvmStatic
    fun main(args: Array<String>) {
        build()
            .args(*args)
            .packages("com.kuvaszuptime.kuvasz")
            .start()
    }
}
