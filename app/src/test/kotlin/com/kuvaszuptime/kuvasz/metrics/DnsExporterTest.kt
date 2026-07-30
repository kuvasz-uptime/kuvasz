package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.check.dns.DnsMonitorActions
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

abstract class DnsExporterTest(env: String, body: BehaviorSpec.() -> Unit = {}) : ExporterTest(env, body) {

    fun dnsMonitorRepository() = appContext?.getBean<DnsMonitorRepository>().shouldNotBeNull()
    fun dnsMonitorActions() = appContext?.getBean<DnsMonitorActions>().shouldNotBeNull()
    fun dnsUptimeEventRepository() = appContext?.getBean<DnsUptimeEventRepository>().shouldNotBeNull()
    fun dnsMetricsLogRepository() = appContext?.getBean<DnsMetricsLogRepository>().shouldNotBeNull()

    val monitorEnableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(DnsMonitorUpdateDto::enabled.name, true)
    val monitorDisableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(DnsMonitorUpdateDto::enabled.name, false)
    val monitorNameUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(DnsMonitorUpdateDto::name.name, "new-name")
}
