package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import java.time.Duration
import java.time.OffsetDateTime

/**
 * The monitor type agnostic view of the uptime event repositories, providing everything that is needed to calculate
 * the uptime related statistics of any monitor type.
 *
 * Decisions are worth knowing about here:
 *
 * - [monitorType] is an extension property instead of a member, so it keeps working on the implementations even when
 *   they are mocked in the tests. A mocked member would return nothing and break every consumer that collects these
 *   repositories as a bean list, like [com.kuvaszuptime.kuvasz.services.StatCalculator].
 */
sealed interface UptimeEventRepository {

    /**
     * Fetches all uptime events that have ended or was open within the specified period.
     */
    fun fetchAllInPeriod(period: Duration, monitorId: Long? = null): List<UptimeEventCalculationContext>

    /**
     * Fetches the timestamp of the latest incident (DOWN status) for enabled monitors.
     */
    fun fetchLatestIncidentTimestamp(): OffsetDateTime?
}
