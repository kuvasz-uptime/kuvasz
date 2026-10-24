package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class HttpCheckCookieJarTest : ShouldSpec({

    fun HttpCheckCookieJar.store(requestUri: String, vararg setCookies: String) =
        storeFrom(responseSettingCookies(*setCookies), requestUri.toUri())

    fun HttpCheckCookieJar.cookieValuesFor(uri: String) = cookiesFor(uri.toUri()).map { "${it.name()}=${it.value()}" }

    context("storing the cookies") {

        should("store every valid cookie of the response, even the ones that don't strictly follow RFC 6265") {
            val jar = HttpCheckCookieJar()

            jar.store(
                "https://example.com/",
                "first=1",
                "second=2; Path=/; HttpOnly",
                """consent={"v":1,"all":true}; Path=/""",
                "=nameless",
            )

            jar.cookieValuesFor("https://example.com/") shouldContainExactlyInAnyOrder
                listOf("first=1", "second=2", """consent={"v":1,"all":true}""")
        }

        should("store every cookie of a response that sets the same name on different paths") {
            val jar = HttpCheckCookieJar()

            jar.store("https://example.com/", "session=new; Path=/", "session=legacy; Path=/legacy")

            jar.cookieValuesFor("https://example.com/") shouldBe listOf("session=new")
            jar.cookieValuesFor("https://example.com/legacy") shouldContainExactly
                listOf("session=legacy", "session=new")
        }

        should("not store anything from a response without cookies") {
            val jar = HttpCheckCookieJar()

            jar.store("https://example.com/")

            jar.cookiesFor("https://example.com/".toUri()).shouldBeEmpty()
        }

        should("not store anything if the request URI has no origin") {
            table(
                headers("requestUri"),
                row("relative/path"),
                row("mailto:someone@example.com"),
            ).forAll { requestUri ->
                val jar = HttpCheckCookieJar()

                jar.store(requestUri, "session=abc")

                jar.cookiesFor(requestUri.toUri()).shouldBeEmpty()
            }
        }

        should("not store a Secure cookie set by an insecure origin") {
            val jar = HttpCheckCookieJar()

            jar.store("http://example.com/", "session=abc; Secure")

            jar.cookiesFor("http://example.com/".toUri()).shouldBeEmpty()
            jar.cookiesFor("https://example.com/".toUri()).shouldBeEmpty()
        }

        should("accept the prefixed cookies only if they meet the requirements of their prefix") {
            table(
                headers("requestUri", "setCookie", "expectAccepted"),
                row("https://example.com/", "__Secure-sid=1; Secure", true),
                row("https://example.com/", "__secure-sid=1; Secure", true),
                row("https://example.com/", "__Secure-sid=1", false),
                row("http://example.com/", "__Secure-sid=1", false),
                row("http://example.com/", "__Secure-sid=1; Secure", false),
                row("https://example.com/", "__Host-sid=1; Secure; Path=/", true),
                row("https://example.com/", "__HOST-sid=1; Secure; Path=/", true),
                row("https://example.com/", "__Host-sid=1; Path=/", false),
                row("http://example.com/", "__Host-sid=1; Path=/", false),
                row("https://example.com/", "__Host-sid=1; Secure", false),
                row("https://example.com/", "__Host-sid=1; Secure; Path=/sub", false),
                row("https://example.com/", "__Host-sid=1; Secure; Path=/; Domain=example.com", false),
            ).forAll { requestUri, setCookie, expectAccepted ->
                val jar = HttpCheckCookieJar()

                jar.store(requestUri, setCookie)

                jar.cookiesFor("${requestUri}sub".toUri()).isNotEmpty() shouldBe expectAccepted
            }
        }

        should("overwrite a cookie with the same name and path, but keep the ones with a different path") {
            val jar = HttpCheckCookieJar()

            jar.store("https://example.com/", "session=old", "session=other; Path=/admin")
            jar.store("https://example.com/", "session=new")

            jar.cookieValuesFor("https://example.com/") shouldBe listOf("session=new")
            // The one with the longer path comes first
            jar.cookieValuesFor("https://example.com/admin") shouldContainExactly
                listOf("session=other", "session=new")
        }

        should("remove a cookie that is expired by a later response") {
            table(
                headers("deletingSetCookie"),
                row("session=; Max-Age=0"),
                row("session=; Max-Age=-1"),
                row("session=; Expires=Thu, 01 Jan 1970 00:00:00 GMT"),
            ).forAll { deletingSetCookie ->
                val jar = HttpCheckCookieJar()
                jar.store("https://example.com/", "session=abc", "other=1")

                jar.store("https://example.com/", deletingSetCookie)

                jar.cookieValuesFor("https://example.com/") shouldBe listOf("other=1")
            }
        }

        should("keep a cookie that expires in the future") {
            val jar = HttpCheckCookieJar()

            jar.store(
                "https://example.com/",
                "session=abc; Max-Age=3600",
                "other=1; Expires=Fri, 01 Jan 2100 00:00:00 GMT",
            )

            jar.cookieValuesFor("https://example.com/") shouldContainExactlyInAnyOrder listOf("session=abc", "other=1")
        }
    }

    context("sending the cookies back") {

        should("send the cookies only to the origin that set them, regardless of their Domain attribute") {
            table(
                headers("setByUri", "requestUri", "expectCookie"),
                row("https://www.example.com/", "https://www.example.com/", true),
                row("https://www.example.com/", "HTTPS://WWW.Example.COM/other", true),
                row("https://www.example.com/", "https://www.example.com:443/", true),
                row("http://www.example.com:80/", "http://www.example.com/", true),
                // A different scheme or port is a different origin, which might even be a different service
                row("https://www.example.com/", "http://www.example.com/", false),
                row("http://www.example.com/", "https://www.example.com/", false),
                row("https://www.example.com/", "https://www.example.com:8443/", false),
                // A different host, even if it's matched by the Domain attribute
                row("https://www.example.com/", "https://example.com/", false),
                row("https://www.example.com/", "https://sub.www.example.com/", false),
                row("https://www.example.com/", "https://other.com/", false),
                // Hosts that can't be parsed by java.net.URI (e.g. containing an underscore)
                row("http://internal_api:8080/", "http://INTERNAL_API:8080/other", true),
                row("http://internal_api:8080/", "http://internal_api:9090/", false),
                row("http://internal_api:8080/", "http://other_api:8080/", false),
                row("https://www.example.com/", "relative/path", false),
            ).forAll { setByUri, requestUri, expectCookie ->
                val jar = HttpCheckCookieJar()
                jar.store(setByUri, "session=abc; Domain=example.com")

                jar.cookiesFor(requestUri.toUri()).isNotEmpty() shouldBe expectCookie
            }
        }

        should("send the cookies only to the matching paths") {
            table(
                headers("setCookie", "setByUri", "requestUri", "expectCookie"),
                // The default path is the "directory" of the request that set the cookie
                row("session=abc", "https://example.com/a/b", "https://example.com/a", true),
                row("session=abc", "https://example.com/a/b", "https://example.com/a/other", true),
                row("session=abc", "https://example.com/a/b", "https://example.com/", false),
                row("session=abc", "https://example.com/a/b", "https://example.com/ab", false),
                row("session=abc", "https://example.com/a", "https://example.com/anything", true),
                row("session=abc", "https://example.com", "https://example.com", true),
                row("session=abc", "https://example.com", "https://example.com/anything", true),
                // An invalid Path attribute falls back to the default path
                row("session=abc; Path=relative", "https://example.com/a/b", "https://example.com/a/c", true),
                row("session=abc; Path=relative", "https://example.com/a/b", "https://example.com/", false),
                // An explicit Path attribute
                row("session=abc; Path=/x", "https://example.com/", "https://example.com/x", true),
                row("session=abc; Path=/x", "https://example.com/", "https://example.com/x/y", true),
                row("session=abc; Path=/x", "https://example.com/", "https://example.com/xy", false),
                row("session=abc; Path=/x", "https://example.com/", "https://example.com/", false),
                row("session=abc; Path=/x/", "https://example.com/", "https://example.com/x/y", true),
                row("session=abc; Path=/x/", "https://example.com/", "https://example.com/x", false),
            ).forAll { setCookie, setByUri, requestUri, expectCookie ->
                val jar = HttpCheckCookieJar()
                jar.store(setByUri, setCookie)

                jar.cookiesFor(requestUri.toUri()).isNotEmpty() shouldBe expectCookie
            }
        }

        should("keep the cookies of an http origin and the https one apart") {
            val jar = HttpCheckCookieJar()

            jar.store("https://example.com/", "session=secure; Secure")
            // E.g. injected by a network attacker on the http hop of the chain
            jar.store("http://example.com/", "session=evil")

            jar.cookieValuesFor("https://example.com/") shouldBe listOf("session=secure")
            jar.cookieValuesFor("http://example.com/") shouldBe listOf("session=evil")
        }
    }

    context("tracking the revisits") {

        should("tell whether a Cookie header would send the same cookies as the last request to a URL") {
            val jar = HttpCheckCookieJar()
            val uri = "https://example.com/gate".toUri()

            // Nothing was recorded yet, which counts as no cookies
            jar.wasSentTo(uri, null).shouldBeTrue()
            jar.wasSentTo(uri, "session=abc").shouldBeFalse()

            jar.recordRequestTo(uri, null)
            jar.wasSentTo(uri, null).shouldBeTrue()
            jar.wasSentTo(uri, "session=abc").shouldBeFalse()

            jar.recordRequestTo(uri, "a=1; b=2")
            jar.wasSentTo(uri, "a=1; b=2").shouldBeTrue()
            // The order of the cookies doesn't matter
            jar.wasSentTo(uri, "b=2;a=1").shouldBeTrue()
            jar.wasSentTo(uri, "a=1; b=3").shouldBeFalse()
            jar.wasSentTo(uri, "a=1").shouldBeFalse()
            jar.wasSentTo("https://example.com/other".toUri(), "a=1; b=2").shouldBeFalse()
        }

        should("remember if the revisit has been used") {
            val jar = HttpCheckCookieJar()

            jar.isRevisitUsed.shouldBeFalse()
            jar.useRevisit()
            jar.isRevisitUsed.shouldBeTrue()
        }
    }
})
