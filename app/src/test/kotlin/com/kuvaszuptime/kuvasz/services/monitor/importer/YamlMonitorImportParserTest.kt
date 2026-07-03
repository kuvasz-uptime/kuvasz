package com.kuvaszuptime.kuvasz.services.monitor.importer

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class YamlMonitorImportParserTest : ShouldSpec({

    val parser = YamlMonitorImportParser()

    context("parsing valid monitor YAML") {

        should("return the parsed DTO") {
            val yaml = """
                http-monitors:
                  - name: test-http
                    url: https://example.com
                    sensitive-url: false
                    uptime-check-interval: 60
                    enabled: true
                    ssl-check-enabled: true
                    request-method: GET
                    latency-history-enabled: true
                    force-no-cache: true
                    follow-redirects: true
                    ssl-expiry-threshold: 30
                    failure-count-threshold: 1
                    integrations: []
                    expected-status-codes: []
                    expected-keyword-case-sensitive: false
                    expected-keyword-negated: false
                    request-headers: {}
                    expected-headers: {}
            """.trimIndent().toByteArray()

            val result = parser.parse(yaml)

            result.httpMonitors shouldBe listOf(
                HttpMonitorExportDto(
                    name = "test-http",
                    url = "https://example.com",
                    sensitiveUrl = false,
                    uptimeCheckInterval = 60,
                    enabled = true,
                    sslCheckEnabled = true,
                    latencyHistoryEnabled = true,
                    requestMethod = HttpMethod.GET,
                    followRedirects = true,
                    forceNoCache = true,
                    sslExpiryThreshold = 30,
                    failureCountThreshold = 1,
                    integrations = emptySet(),
                    expectedStatusCodes = emptySet(),
                    responseTimeThresholdMillis = null,
                    expectedKeyword = null,
                    expectedKeywordCaseSensitive = false,
                    expectedKeywordNegated = false,
                    requestHeaders = emptyMap(),
                    expectedHeaders = emptyMap(),
                    requestBody = null,
                )
            )
            result.pushMonitors shouldBe null
            result.icmpMonitors shouldBe null
        }

        should("return the parsed DTO for all monitor types") {
            val yaml = """
                http-monitors:
                  - name: test-http
                    url: https://example.com
                    sensitive-url: false
                    uptime-check-interval: 60
                    enabled: true
                    ssl-check-enabled: true
                    request-method: GET
                    latency-history-enabled: true
                    force-no-cache: true
                    follow-redirects: true
                    ssl-expiry-threshold: 30
                    failure-count-threshold: 1
                    integrations: []
                    expected-status-codes: []
                    expected-keyword-case-sensitive: false
                    expected-keyword-negated: false
                    request-headers: {}
                    expected-headers: {}
                push-monitors:
                  - name: test-push
                    heartbeat-interval: 60
                    grace-period: 30
                    client-secret: secret
                    enabled: true
                    integrations: []
                    failure-count-threshold: 1
                icmp-monitors:
                  - name: test-icmp
                    host: 1.2.3.4
                    uptime-check-interval: 60
                    packet-count: 4
                    timeout-seconds: 5
                    packet-loss-threshold: 50
                    failure-count-threshold: 1
                    enabled: true
                    integrations: []
                    metrics-history-enabled: true
            """.trimIndent().toByteArray()

            val result = parser.parse(yaml)

            result.httpMonitors!!.size shouldBe 1
            result.httpMonitors!!.first().name shouldBe "test-http"
            result.pushMonitors!!.size shouldBe 1
            result.pushMonitors!!.first().name shouldBe "test-push"
            result.icmpMonitors!!.size shouldBe 1
            result.icmpMonitors!!.first().name shouldBe "test-icmp"
        }
    }

    context("parsing invalid YAML") {

        should("throw when the YAML is malformed") {
            val malformedYaml = "not: valid: [".toByteArray()

            shouldThrow<Exception> {
                parser.parse(malformedYaml)
            }
        }

        should("throw when a monitor section has the wrong type") {
            val wrongTypeYaml = """
                http-monitors: not-a-list
            """.trimIndent().toByteArray()

            shouldThrow<Exception> {
                parser.parse(wrongTypeYaml)
            }
        }
    }

    context("parsing edge cases") {

        should("ignore unknown top-level keys") {
            val yaml = """
                http-monitors: []
                unknown-section:
                  - key: value
            """.trimIndent().toByteArray()

            val result = parser.parse(yaml)

            result.httpMonitors shouldBe emptyList()
            result.pushMonitors shouldBe null
            result.icmpMonitors shouldBe null
        }

        should("parse empty explicit lists") {
            val yaml = """
                http-monitors: []
                push-monitors: []
                icmp-monitors: []
            """.trimIndent().toByteArray()

            val result = parser.parse(yaml)

            result.httpMonitors shouldBe emptyList()
            result.pushMonitors shouldBe emptyList()
            result.icmpMonitors shouldBe emptyList()
        }
    }
})
