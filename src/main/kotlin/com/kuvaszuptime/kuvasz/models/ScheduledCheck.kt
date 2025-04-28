package com.kuvaszuptime.kuvasz.models

import java.util.concurrent.ScheduledFuture

data class ScheduledCheck(
    val checkType: CheckType,
    val monitorId: Long,
    val task: ScheduledFuture<*>
)

enum class CheckType {
    UPTIME, SSL
}
