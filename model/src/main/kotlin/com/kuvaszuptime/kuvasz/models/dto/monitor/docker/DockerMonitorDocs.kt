package com.kuvaszuptime.kuvasz.models.dto.monitor.docker

object DockerMonitorDocs {
    const val DOCKER_HOST = "The name of the configured Docker host the container runs on"
    const val CONTAINER = "The name or ID of the container to inspect"
    const val IMAGE = "The image the container was created from, as the latest check reported it. " +
        "Null if the container could not be inspected"
    const val EVENT_IMAGE = "The image the container was created from, as last reported during the event. " +
        "Null if the container could not be inspected"
    const val RESTART_COUNT = "How many times the container's restart policy has restarted it, as the latest " +
        "inspection reported it. Reset to zero by a manual start or restart. Null if it is not known yet"
    const val EVENT_RESTART_COUNT = "How many times the container's restart policy has restarted it, as last " +
        "reported during the event. Null if it is not known"
    const val METRICS_RESTART_COUNT = "How many times the container's restart policy had restarted it at the time " +
        "of the check. Null if the container could not be inspected"
    const val METRICS_CONTAINER_CREATED_AT = "When the container was created, as the check found it. It tells the " +
        "restart counts of a recreated container apart. Null if the container could not be inspected"
    const val CONTAINER_CREATED_AT = "When the container was created, as the latest inspection reported it. " +
        "It changes when the container is recreated. Null if it is not known yet"
    const val EVENT_CONTAINER_CREATED_AT = "When the container was created, as last reported during the event. " +
        "Null if it is not known"
    const val TIMEOUT_MS = "The Docker API request timeout in milliseconds (1-30000)"
    const val METRICS_HISTORY_ENABLED =
        "Whether metrics history is enabled for the monitor. Beyond recording the history, it also turns on the " +
            "CPU and memory sampling of the container, which costs an extra Docker API call on every check."
    const val RESTART_ALERT_ENABLED =
        "Whether a DOCKER_CONTAINER_RESTARTED notification is sent when the restart policy of the container restarts " +
            "it between two checks. Manual restarts and recreating the container do not trigger it."
    const val MONITORS_405_REASON =
        "Docker monitors are in read-only mode, because they are loaded from a YAML config file"
}
