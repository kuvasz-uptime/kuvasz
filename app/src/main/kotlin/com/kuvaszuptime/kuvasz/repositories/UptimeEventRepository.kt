package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
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
     * Fetches the uptime events that were open at any point of the period: the ones that started by its end, and
     * either haven't ended yet or ended after its start. Both ends are passed in, so they are anchored to the very same
     * instant as the calculations relying on the events.
     *
     * @param onlyEnabledMonitors Whether the events of the monitors that are paused now should be left out.
     */
    fun fetchAllInPeriod(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>? = null,
        onlyEnabledMonitors: Boolean = false,
    ): List<UptimeEventCalculationContext>

    /**
     * Fetches the timestamp of the latest incident (DOWN status) for enabled monitors.
     */
    fun fetchLatestIncidentTimestamp(): OffsetDateTime?
}
