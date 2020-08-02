package com.akobor.kuvasz.mocks

import com.akobor.kuvasz.config.AppConfig
import io.micronaut.security.authentication.UsernamePasswordCredentials

fun generateCredentials(appConfig: AppConfig, valid: Boolean) =
    UsernamePasswordCredentials(
        appConfig.user,
        if (valid) appConfig.password else "bad-pass"
    )
