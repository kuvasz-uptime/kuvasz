package com.akobor.kuvasz

import io.micronaut.runtime.Micronaut.build
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info

@OpenAPIDefinition(
    info = Info(
        title = "kuvasz",
        version = "latest"
    )
)
@Suppress("SpreadOperator")
object Application {
    @JvmStatic
    fun main(args: Array<String>) {
        build()
            .args(*args)
            .packages("com.akobor.kuvasz")
            .start()
    }
}
