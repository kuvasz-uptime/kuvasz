package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import java.time.OffsetDateTime

/** How the [monitorListQueryTests] seed the monitors of a type, and what the list of the type has on top. */
class MonitorListUiTestConfig(
    // As it appears in the routes and the test ids, e.g. `push`
    val slug: String,
    val createMonitor: (name: String, category: String?, enabled: Boolean) -> Unit,
    // Starts the given status of the monitor with the given name, which is its current one from then on
    val startUptimeStatus: (monitorName: String, status: UptimeStatus, startedAt: OffsetDateTime) -> Unit,
    // The sortable columns of the type, beyond the name, the status and the time of its last change
    val otherSortFields: List<String> = emptyList(),
) {
    val listPath = "/$slug-monitors"
}
