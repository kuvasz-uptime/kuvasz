package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.DockerMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DockerUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.check.docker.DockerMonitorActions
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

abstract class DockerExporterTest(env: String, body: BehaviorSpec.() -> Unit = {}) : ExporterTest(env, body) {

    fun dockerMonitorRepository() = appContext?.getBean<DockerMonitorRepository>().shouldNotBeNull()
    fun dockerMonitorActions() = appContext?.getBean<DockerMonitorActions>().shouldNotBeNull()
    fun dockerUptimeEventRepository() = appContext?.getBean<DockerUptimeEventRepository>().shouldNotBeNull()
    fun dockerMetricsLogRepository() = appContext?.getBean<DockerMetricsLogRepository>().shouldNotBeNull()

    val monitorEnableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(DockerMonitorUpdateDto::enabled.name, true)
    val monitorDisableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(DockerMonitorUpdateDto::enabled.name, false)
    val monitorNameUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(DockerMonitorUpdateDto::name.name, "new-name")
}
