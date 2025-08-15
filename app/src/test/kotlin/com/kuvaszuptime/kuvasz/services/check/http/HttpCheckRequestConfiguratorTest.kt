package com.kuvaszuptime.kuvasz.services.check.http

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
    )

    context("the configurator logic") {

        should("create the right request for every possible permutation of monitoring properties") {

            table(
                headers("monitorMethod", "forceNoCache", "expectedKeyword", "testExpectation"),
                row(
                    HttpMethod.GET, false, null, TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.GET,
                        expectedUri = "https://example.com",
                        expectedCacheControl = null,
                        expectedAcceptedEncoding = "gzip, deflate, br",
                    )
                ),
                row(
                    HttpMethod.GET, true, null, TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.GET,
                        expectedUri = "https://example.com",
                        expectedCacheControl = "no-cache",
                        expectedAcceptedEncoding = "gzip, deflate, br",
                    )
                ),
                row(
                    HttpMethod.GET, false, "keyword", TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.GET,
                        expectedUri = "https://example.com",
                        expectedCacheControl = null,
                        expectedAcceptedEncoding = null,
                    )
                ),
                row(
                    HttpMethod.GET, true, "keyword", TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.GET,
                        expectedUri = "https://example.com",
                        expectedCacheControl = "no-cache",
                        expectedAcceptedEncoding = null,
                    )
                ),
                row(
                    HttpMethod.HEAD, false, null, TestExpectation(
                        expectedMethod = io.micronaut.http.HttpMethod.HEAD,
                        expectedUri = "https://example.com",
                        expectedCacheControl = null,
                        expectedAcceptedEncoding = "gzip, deflate, br",
                    )
                ),
            ).forAll { monitorMethod, forceNoCache, expectedKeyword, testExpectation ->

                val monitor = MonitorRecord().apply {
                    requestMethod = monitorMethod
                    this.forceNoCache = forceNoCache
                    this.expectedKeyword = expectedKeyword
                    this.url = "https://irrelevant.com" // URL will be overridden by the URI
                }
                val requestUri = "https://example.com".toUri()

                val request = configurator.fromMonitor(monitor, requestUri)

                request.method shouldBe testExpectation.expectedMethod
                request.uri.toString() shouldBe testExpectation.expectedUri
                request.headers.get(HttpHeaders.ACCEPT) shouldBe "*/*"
                request.headers.get(HttpHeaders.USER_AGENT) shouldBe HttpCheckRequestConfigurator.USER_AGENT
                request.headers.get(HttpHeaders.CACHE_CONTROL) shouldBe testExpectation.expectedCacheControl
                request.headers.get(HttpHeaders.ACCEPT_ENCODING) shouldBe testExpectation.expectedAcceptedEncoding
            }
        }
    }
})
