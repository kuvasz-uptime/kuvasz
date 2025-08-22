package com.kuvaszuptime.kuvasz.services.check.http

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpHeaders

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

                val monitor = MonitorRecord().apply {
                    requestMethod = monitorMethod
                    this.forceNoCache = forceNoCache
                    this.expectedKeyword = expectedKeyword
                    this.url = "https://irrelevant.com" // URL will be overridden by the URI
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
            val monitor = MonitorRecord().apply {
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

                val monitor = MonitorRecord().apply {
                    requestMethod = method
                    requestBody = body
                    forceNoCache = false // irrelevant for this test
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
