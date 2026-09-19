package com.kuvaszuptime.kuvasz.models

enum class MonitorType(val identifier: String) {
    HTTP_SSL("http"),
    PUSH("push"),
    ICMP("icmp"),
    TCP("tcp"),
    DNS("dns"),
    DOCKER("docker");

    companion object {
        fun fromIdentifier(identifier: String): MonitorType? =
            entries.find { it.identifier == identifier }
    }
}
