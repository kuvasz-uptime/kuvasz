package com.kuvaszuptime.kuvasz.models

import io.micronaut.core.annotation.Introspected

@Introspected
enum class IncidentType {
    HTTP,
    SSL,
    PUSH,
    ICMP,
    TCP,
    DNS,
    DOCKER;

    companion object {

        fun fromNameOrNull(name: String): IncidentType? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

val IncidentType.isShownPublicly: Boolean
    get() = this != IncidentType.SSL

val MonitorType.uptimeIncidentType: IncidentType
    get() = when (this) {
        MonitorType.HTTP_SSL -> IncidentType.HTTP
        MonitorType.PUSH -> IncidentType.PUSH
        MonitorType.ICMP -> IncidentType.ICMP
        MonitorType.TCP -> IncidentType.TCP
        MonitorType.DNS -> IncidentType.DNS
        MonitorType.DOCKER -> IncidentType.DOCKER
    }

val IncidentType.monitorType: MonitorType
    get() = when (this) {
        IncidentType.HTTP, IncidentType.SSL -> MonitorType.HTTP_SSL
        IncidentType.PUSH -> MonitorType.PUSH
        IncidentType.ICMP -> MonitorType.ICMP
        IncidentType.TCP -> MonitorType.TCP
        IncidentType.DNS -> MonitorType.DNS
        IncidentType.DOCKER -> MonitorType.DOCKER
    }
