package com.kuvaszuptime.kuvasz.mocks

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import io.micronaut.security.authentication.UsernamePasswordCredentials

fun generateCredentials(authConfig: AdminAuthConfig, valid: Boolean) =
    UsernamePasswordCredentials(
        authConfig.username,
        if (valid) authConfig.password else "bad-pass"
    )
