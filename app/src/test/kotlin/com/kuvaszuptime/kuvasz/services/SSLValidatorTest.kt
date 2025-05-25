package com.kuvaszuptime.kuvasz.services

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.booleans.shouldBeTrue
import java.net.URL

class SSLValidatorTest : StringSpec(
    {
        val validator = SSLValidator()

        "validate should return the right result" {
            table(
                headers("url", "isValid"),
                row("https://github.com", true),
                row("https://google.com", true),
                row("https://akobor.me", true),

                row("https://test-ev-rsa.ssl.com/", true),
                row("https://test-dv-rsa.ssl.com/", true),
                row("https://test-ev-ecc.ssl.com/", true),
                row("https://test-dv-ecc.ssl.com/", true),

                row("https://sha256.badssl.com/", true),
                row("https://ecc256.badssl.com/", true),
                row("https://ecc384.badssl.com/", true),
                row("https://rsa2048.badssl.com/", true),
                row("https://rsa4096.badssl.com/", true),

                row("http://test-ev-rsa.ssl.com/", false),
                row("https://expired-rsa-dv.ssl.com", false),
                row("https://expired-rsa-ev.ssl.com", false),
                row("https://expired-ecc-dv.ssl.com", false),
                row("https://expired-ecc-ev.ssl.com", false),

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
