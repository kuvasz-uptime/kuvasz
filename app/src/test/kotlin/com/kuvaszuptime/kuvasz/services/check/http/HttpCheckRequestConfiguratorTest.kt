package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpHeaders
import tools.jackson.databind.node.JsonNodeFactory

@Suppress("Wrapping", "ArgumentListWrapping")
class HttpCheckRequestConfiguratorTest : ShouldSpec({

    val configurator = HttpCheckRequestConfigurator()

    data class TestExpectation(
        val expectedMethod: io.micronaut.http.HttpMethod,
        val expectedUri: String,
        val expectedCacheControl: String?,
        val expectedAcceptedEncoding: String?,
        val expectedBody: String?,
    )

    context("the configurator logic - basic logic with built-in headers and method") {

        should("create the right request for every possible permutation of monitoring properties") {

            table(
                headers("monitorMethod", "forceNoCache", "expectedKeyword", "requestBody", "testExpectation"),
                row(
                    HttpMethod.GET, false, null, null,
                    TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.GET,
                        expectedUri = "https://example.com",
                        expectedCacheControl = null,
                        expectedAcceptedEncoding = "gzip, deflate, br",
                        expectedBody = null,
                    )
                ),
                row(
                    HttpMethod.GET, true, null, null,
                    TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.GET,
                        expectedUri = "https://example.com",
                        expectedCacheControl = "no-cache",
                        expectedAcceptedEncoding = "gzip, deflate, br",
                        expectedBody = null,
                    )
                ),
                row(
                    HttpMethod.GET, false, "keyword", null,
                    TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.GET,
                        expectedUri = "https://example.com",
                        expectedCacheControl = null,
                        expectedAcceptedEncoding = null,
                        expectedBody = null,
                    )
                ),
                row(
                    HttpMethod.GET, true, "keyword", null,
                    TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.GET,
                        expectedUri = "https://example.com",
                        expectedCacheControl = "no-cache",
                        expectedAcceptedEncoding = null,
                        expectedBody = null,
                    )
                ),
                row(
                    HttpMethod.HEAD, false, null, null,
                    TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.HEAD,
                        expectedUri = "https://example.com",
                        expectedCacheControl = null,
                        expectedAcceptedEncoding = "gzip, deflate, br",
                        expectedBody = null,
                    )
                ),
                row(
                    HttpMethod.GET, false, null, """{"something": "test"}""",
                    TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.GET,
                        expectedUri = "https://example.com",
                        expectedCacheControl = null,
                        expectedAcceptedEncoding = "gzip, deflate, br",
                        expectedBody = null,
                    )
                ),
                row(
                    HttpMethod.POST, false, null, """{"something": "test"}""",
                    TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.POST,
                        expectedUri = "https://example.com",
                        expectedCacheControl = null,
                        expectedAcceptedEncoding = "gzip, deflate, br",
                        expectedBody = """{"something": "test"}""",
                    )
                ),
            ).forAll { monitorMethod, forceNoCache, expectedKeyword, requestBody, testExpectation ->

                val monitor = HttpMonitorRecord().apply {
                    requestMethod = monitorMethod
                    this.forceNoCache = forceNoCache
                    this.expectedKeyword = expectedKeyword
                    this.url = "https://irrelevant.com" // URL will be overridden by the URI
                    this.crossOriginHeaderPropagation = false
                    this.requestHeaders = JsonNodeFactory.instance.objectNode()
                    this.requestBody = requestBody
                }
                val requestUri = "https://example.com".toUri()

                val request = configurator.fromMonitor(monitor, requestUri)

                request.method shouldBe testExpectation.expectedMethod
                request.uri.toString() shouldBe testExpectation.expectedUri
                request.headers.get(HttpHeaders.ACCEPT) shouldBe "*/*"
                request.headers.get(HttpHeaders.USER_AGENT) shouldBe HttpCheckRequestConfigurator.USER_AGENT
                request.headers.get(HttpHeaders.CACHE_CONTROL) shouldBe testExpectation.expectedCacheControl
                request.headers.get(HttpHeaders.ACCEPT_ENCODING) shouldBe testExpectation.expectedAcceptedEncoding
                request.getBody(String::class.java).orElse(null) shouldBe testExpectation.expectedBody
            }
        }
    }

    context("the configurator logic - custom request headers") {

        should("add custom request headers to the request") {
            val monitor = HttpMonitorRecord().apply {
                url = "https://example.com"
                crossOriginHeaderPropagation = false
                forceNoCache = true
                requestMethod = HttpMethod.GET
                requestHeaders = JsonNodeFactory.instance.objectNode().apply {
                    put("X-Custom-Header", "CustomValue")
                    put("X-Another-Header", "AnotherValue")
                    // Should override the one set by "forceNoCache"
                    put(HttpHeaders.CACHE_CONTROL, "something else")
                    // Should override the built-in User-Agent header
                    put(HttpHeaders.USER_AGENT, "CustomUserAgent/1.0")
                    // Should override the built-in Accept-Encoding header
                    put(HttpHeaders.ACCEPT_ENCODING, "custom")
                    // Should overrie the built-in Accept header
                    put(HttpHeaders.ACCEPT, "another custom")
                }
            }
            val requestUri = "https://example.com".toUri()

            val request = configurator.fromMonitor(monitor, requestUri)

            request.headers.get("X-Custom-Header") shouldBe "CustomValue"
            request.headers.get("X-Another-Header") shouldBe "AnotherValue"
            request.headers.get(HttpHeaders.CACHE_CONTROL) shouldBe "something else"
            request.headers.get(HttpHeaders.USER_AGENT) shouldBe "CustomUserAgent/1.0"
            request.headers.get(HttpHeaders.ACCEPT_ENCODING) shouldBe "custom"
            request.headers.get(HttpHeaders.ACCEPT) shouldBe "another custom"
        }
    }

    context("the configurator logic - custom request headers on a different origin") {

        should("withhold the custom request headers from other origins unless propagation is enabled") {

            table(
                headers("monitorUrl", "requestUri", "crossOriginHeaderPropagation", "expectCustomHeaders"),
                // Same origin
                row("https://example.com/path", "https://example.com/other", false, true),
                row("https://example.com", "HTTPS://EXAMPLE.com/other", false, true),
                row("https://example.com", "https://example.com:443/other", false, true),
                row("http://example.com:80", "http://example.com/other", false, true),
                row("https://example.com:8443", "https://example.com:8443/other", false, true),
                row("http://internal_api:8080/health", "http://INTERNAL_API:8080/other", false, true),
                // Different origin
                row("https://example.com", "http://example.com/other", false, false),
                row("http://example.com", "https://example.com/other", false, false),
                row("https://example.com", "https://example.com:8443/other", false, false),
                row("https://example.com", "https://other.com/path", false, false),
                row("https://example.com", "https://sub.example.com", false, false),
                // Hosts that can't be parsed by java.net.URI (e.g. containing an underscore)
                row("http://internal_api:8080", "http://other_svc:9090/path", false, false),
                row("http://internal_api:8080", "http://other_svc:8080/path", false, false),
                row("http://internal_api:8080", "http://internal_api:9090/path", false, false),
                row("https://example.com", "https://some_host.example.com/path", false, false),
                row("http://internal_api", "http://example.com/path", false, false),
                // Propagation is enabled
                row("https://example.com", "https://other.com/path", true, true),
                row("https://example.com", "http://example.com:8080/other", true, true),
                row("https://example.com", "https://example.com/other", true, true),
            ).forAll { monitorUrl, requestUri, crossOriginHeaderPropagation, expectCustomHeaders ->

                val monitor = HttpMonitorRecord().apply {
                    url = monitorUrl
                    requestMethod = HttpMethod.GET
                    forceNoCache = true
                    this.crossOriginHeaderPropagation = crossOriginHeaderPropagation
                    requestHeaders = JsonNodeFactory.instance.objectNode().apply {
                        put(HttpHeaders.AUTHORIZATION, "Bearer secret")
                        put(HttpHeaders.USER_AGENT, "CustomUserAgent/1.0")
                    }
                }

                val request = configurator.fromMonitor(monitor, requestUri.toUri())

                if (expectCustomHeaders) {
                    request.headers.get(HttpHeaders.AUTHORIZATION) shouldBe "Bearer secret"
                    request.headers.get(HttpHeaders.USER_AGENT) shouldBe "CustomUserAgent/1.0"
                } else {
                    request.headers.get(HttpHeaders.AUTHORIZATION) shouldBe null
                    // The built-in headers are sent regardless
                    request.headers.get(HttpHeaders.USER_AGENT) shouldBe HttpCheckRequestConfigurator.USER_AGENT
                }
                request.headers.get(HttpHeaders.ACCEPT) shouldBe "*/*"
                request.headers.get(HttpHeaders.CACHE_CONTROL) shouldBe "no-cache"
            }
        }
    }

    context("the configurator logic - custom request body and method") {

        should("add the body to the requests with the supported methods") {

            val testBody = """{"key": "value"}"""
            table(
                headers("method", "body", "expectedBody", "expectedMethod"),
                row(HttpMethod.POST, testBody, testBody, io.micronaut.http.HttpMethod.POST),
                row(HttpMethod.PUT, testBody, testBody, io.micronaut.http.HttpMethod.PUT),
                row(HttpMethod.PATCH, testBody, testBody, io.micronaut.http.HttpMethod.PATCH),
                row(HttpMethod.DELETE, testBody, null, io.micronaut.http.HttpMethod.DELETE),
                row(HttpMethod.GET, testBody, null, io.micronaut.http.HttpMethod.GET),
                row(HttpMethod.HEAD, testBody, null, io.micronaut.http.HttpMethod.HEAD),
                row(HttpMethod.OPTIONS, testBody, null, io.micronaut.http.HttpMethod.OPTIONS),
            ).forAll { method, body, expectedBody, expectedMethod ->

                val monitor = HttpMonitorRecord().apply {
                    requestMethod = method
                    requestBody = body
                    forceNoCache = false // irrelevant for this test
                    url = "https://example.com" // irrelevant for this test
                    crossOriginHeaderPropagation = false // irrelevant for this test
                    requestHeaders = JsonNodeFactory.instance.objectNode() // irrelevant for this test
                }
                val requestUri = "https://example.com".toUri()

                val request = configurator.fromMonitor(monitor, requestUri)

                request.method shouldBe expectedMethod
                request.getBody(String::class.java).orElse(null) shouldBe expectedBody
            }
        }
    }
})
