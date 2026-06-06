package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorActions
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

abstract class IcmpExporterTest(env: String, body: BehaviorSpec.() -> Unit = {}) : ExporterTest(env, body) {

    fun icmpMonitorRepository() = appContext?.getBean<IcmpMonitorRepository>().shouldNotBeNull()
    fun icmpMonitorActions() = appContext?.getBean<IcmpMonitorActions>().shouldNotBeNull()
    fun icmpUptimeEventRepository() = appContext?.getBean<IcmpUptimeEventRepository>().shouldNotBeNull()
    fun icmpMetricsLogRepository() = appContext?.getBean<IcmpMetricsLogRepository>().shouldNotBeNull()

    val monitorEnableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(IcmpMonitorUpdateDto::enabled.name, true)
    val monitorDisableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(IcmpMonitorUpdateDto::enabled.name, false)
    val monitorNameUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(IcmpMonitorUpdateDto::name.name, "new-name")
}
