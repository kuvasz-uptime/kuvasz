package com.kuvaszuptime.kuvasz.models

enum class MonitorType(val identifier: String) {
    HTTP_SSL("http"),
    PUSH("push");

    companion object {
        fun fromIdentifier(identifier: String): MonitorType? =
            entries.find { it.identifier == identifier }
    }
}
