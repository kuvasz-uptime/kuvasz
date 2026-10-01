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
    DOCKER,
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
