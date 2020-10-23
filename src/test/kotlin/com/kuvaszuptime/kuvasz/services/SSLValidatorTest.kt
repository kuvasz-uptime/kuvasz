package com.kuvaszuptime.kuvasz.services

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.booleans.shouldBeTrue
import java.net.URL
import kotlin.time.seconds

class SSLValidatorTest : DescribeSpec(
    {
        val validator = SSLValidator()

        describe("SSLValidator") {
            it("validate should return the right result").config(timeout = 30.seconds) {
                table(
                    headers("url", "isValid"),
                    row("https://sha256.badssl.com/", true),
                    row("https://sha384.badssl.com/", true),
                    row("https://sha512.badssl.com/", true),
                    row("https://1000-sans.badssl.com/", true),
                    row("https://10000-sans.badssl.com/", true),
                    row("https://ecc256.badssl.com/", true),
                    row("https://ecc384.badssl.com/", true),
                    row("https://rsa2048.badssl.com/", true),
                    row("https://rsa4096.badssl.com/", true),
                    row("https://rsa8192.badssl.com/", true),
                    row("https://extended-validation.badssl.com/", true),

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
    }
)
