package com.akobor.kuvasz.mocks

import io.micronaut.security.authentication.UsernamePasswordCredentials

fun generateCredentials(valid: Boolean) =
    UsernamePasswordCredentials(
        "test-user",
        if (valid) "test-pass" else "bad-pass"
    )
