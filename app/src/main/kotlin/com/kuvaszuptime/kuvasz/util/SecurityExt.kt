package com.kuvaszuptime.kuvasz.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Compares the strings in constant time to avoid leaking information through timing differences when
 * checking secrets (API keys, passwords, etc.)
 */
fun String?.constantTimeEquals(other: String?): Boolean {
    if (this == null || other == null) return false

    return MessageDigest.isEqual(
        toByteArray(StandardCharsets.UTF_8),
        other.toByteArray(StandardCharsets.UTF_8),
    )
}
