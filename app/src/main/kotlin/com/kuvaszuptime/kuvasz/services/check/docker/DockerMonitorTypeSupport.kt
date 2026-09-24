package com.kuvaszuptime.kuvasz.services.check.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.docker.DockerMonitorCreator
import com.kuvaszuptime.kuvasz.repositories.DockerMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import com.kuvaszuptime.kuvasz.services.monitor.MetricsHistoryMonitorTypeSupport
import com.kuvaszuptime.kuvasz.validation.requireConfiguredHost
import jakarta.inject.Singleton

@Singleton
class DockerMonitorTypeSupport(
    override val repository: DockerMonitorRepository,
    override val metricsLogRepository: DockerMetricsLogRepository,
    // Absent when no Docker hosts are configured at all
    private val hostRegistry: DockerHostRegistry?,
) : MetricsHistoryMonitorTypeSupport<DockerMonitorCreator, DockerMonitorRecord, DockerMonitorDetailsDto>() {

    /**
     * A new monitor has to name a configured host, but an existing one keeps its host even after that was removed
     * from the config - a YAML monitor re-imported after a restart included -, so its checks can report it.
     */
    override fun beforeUpsert(previous: DockerMonitorRecord?, toUpsert: DockerMonitorRecord) {
        if (previous == null) {
            hostRegistry.requireConfiguredHost(toUpsert.dockerHost)
        }
    }
}
