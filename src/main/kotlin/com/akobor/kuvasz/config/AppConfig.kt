package com.akobor.kuvasz.config

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected

@ConfigurationProperties("app-config")
@Introspected
class AppConfig {
    var user: String? = null
    var password: String? = null
}
