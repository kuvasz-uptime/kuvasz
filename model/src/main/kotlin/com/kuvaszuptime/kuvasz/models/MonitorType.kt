package com.kuvaszuptime.kuvasz.models

enum class MonitorType(val identifier: String) {
    HTTP_SSL("http"),
    PUSH("push"),
    ICMP("icmp"),
    TCP("tcp");

    companion object {
        fun fromIdentifier(identifier: String): MonitorType? =
            entries.find { it.identifier == identifier }
    }
}
