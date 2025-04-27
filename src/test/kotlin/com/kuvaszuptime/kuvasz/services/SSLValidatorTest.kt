package com.kuvaszuptime.kuvasz.services

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.booleans.shouldBeTrue
import java.net.URL

/**
 * FIXME
 * The commented lines are broken, because badssl.com has some issues with their certificates for a while now.
 * Since the logic behind wasn't changed since it's broke, we can assume that SSL check still works, but better stay
 * on the safe side and fix this test later
 */
class SSLValidatorTest : StringSpec(
    {
        val validator = SSLValidator()

        "validate should return the right result" {
            table(
                headers("url", "isValid"),
                row("https://sha256.badssl.com/", true),
//                row("https://sha384.badssl.com/", true),
//                row("https://sha512.badssl.com/", true),
                row("https://ecc256.badssl.com/", true),
                row("https://ecc384.badssl.com/", true),
                row("https://rsa2048.badssl.com/", true),
                row("https://rsa4096.badssl.com/", true),
//                row("https://rsa8192.badssl.com/", true),
//                row("https://extended-validation.badssl.com/", true),

                row("https://expired.badssl.com/", false),
                row("https://wrong.host.badssl.com/", false),
                row("https://self-signed.badssl.com/", false),
                row("https://untrusted-root.badssl.com/", false),
                row("https://no-common-name.badssl.com/", false),
                row("https://no-subject.badssl.com/", false),
                row("https://incomplete-chain.badssl.com/", false)
            ).forAll { url, isValid ->
                val result = validator.validate(URL(url))

                (if (isValid) result.isRight() else result.isLeft()).shouldBeTrue()
            }
        }
    }
)
