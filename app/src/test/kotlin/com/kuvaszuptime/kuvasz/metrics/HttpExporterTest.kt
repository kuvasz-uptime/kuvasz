package com.kuvaszuptime.kuvasz.metrics

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull

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
}
