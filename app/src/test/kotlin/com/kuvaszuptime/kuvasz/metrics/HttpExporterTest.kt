package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.importing.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

abstract class HttpExporterTest(env: String, body: BehaviorSpec.() -> Unit = {}) : ExporterTest(env, body) {

    fun latencyLogRepository() = appContext?.getBean<HttpLatencyLogRepository>().shouldNotBeNull()
    fun httpMonitorRepository() = appContext?.getBean<HttpMonitorRepository>().shouldNotBeNull()
    fun httpMonitorActions() = appContext?.getBean<HttpMonitorActions>().shouldNotBeNull()
    fun sslEventRepository() = appContext?.getBean<SSLEventRepository>().shouldNotBeNull()
    fun httpUptimeEventRepository() = appContext?.getBean<HttpUptimeEventRepository>().shouldNotBeNull()

    val monitorEnableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(HttpMonitorUpdateDto::enabled.name, true)
    val monitorDisableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(HttpMonitorUpdateDto::enabled.name, false)
    val monitorNameUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(HttpMonitorUpdateDto::name.name, "new-name")
    val monitorSSLEnableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(HttpMonitorUpdateDto::sslCheckEnabled.name, true)

    fun httpImportAdapter(name: String, url: String, enabled: Boolean = true) = HttpMonitorImportAdapter(
        HttpMonitorExportDto(
            name = name,
            url = url,
            sensitiveUrl = false,
            // A long interval keeps the freshly scheduled checks from firing during the test
            uptimeCheckInterval = 30000,
            enabled = enabled,
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
}
