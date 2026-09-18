package com.kuvaszuptime.kuvasz.models.settings

import java.time.OffsetDateTime

enum class ConnectivityState {
    UNKNOWN,
    UP,
    DOWN,
}

data class ConnectivityStatus(
    val state: ConnectivityState,
    val targets: List<String>,
    val intervalSeconds: Long,
    val timeoutSeconds: Long,
    val lastCheckedAt: OffsetDateTime?,
    val lastSuccessfulCheckAt: OffsetDateTime?,
    val downSince: OffsetDateTime?,
    val lastError: String?,
) {
    val areChecksSuspended: Boolean = state == ConnectivityState.DOWN
}
