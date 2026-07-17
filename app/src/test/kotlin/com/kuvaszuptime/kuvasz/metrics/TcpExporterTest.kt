package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpMonitorActions
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

abstract class TcpExporterTest(env: String, body: BehaviorSpec.() -> Unit = {}) : ExporterTest(env, body) {

    fun tcpMonitorRepository() = appContext?.getBean<TcpMonitorRepository>().shouldNotBeNull()
    fun tcpMonitorActions() = appContext?.getBean<TcpMonitorActions>().shouldNotBeNull()
    fun tcpUptimeEventRepository() = appContext?.getBean<TcpUptimeEventRepository>().shouldNotBeNull()
    fun tcpMetricsLogRepository() = appContext?.getBean<TcpMetricsLogRepository>().shouldNotBeNull()

    val monitorEnableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(TcpMonitorUpdateDto::enabled.name, true)
    val monitorDisableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(TcpMonitorUpdateDto::enabled.name, false)
    val monitorNameUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(TcpMonitorUpdateDto::name.name, "new-name")
}
