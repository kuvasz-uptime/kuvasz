package com.kuvaszuptime.kuvasz.models.dto.monitor.docker

object DockerMonitorDocs {
    const val DOCKER_HOST = "The name of the configured Docker host the container runs on"
    const val CONTAINER = "The name or ID of the container to inspect"
    const val TIMEOUT_MS = "The Docker API request timeout in milliseconds (1-30000)"
    const val METRICS_HISTORY_ENABLED =
        "Whether metrics history is enabled for the monitor. Beyond recording the history, it also turns on the " +
            "CPU and memory sampling of the container, which costs an extra Docker API call on every check."
    const val MONITORS_405_REASON =
        "Docker monitors are in read-only mode, because they are loaded from a YAML config file"
}
