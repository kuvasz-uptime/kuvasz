package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.MonitorType

/**
 * The type of the monitors the given repository is dealing with.
 *
 * It is an extension property instead of a member of [MonitorRepository] on purpose, so that it keeps working on the
 * implementations even when they are mocked in the tests. A mocked member would return nothing and break every
 * consumer that collects these repositories as a bean list, like [com.kuvaszuptime.kuvasz.services.StatCalculator].
 */
val MonitorRepository<*, *>.monitorType: MonitorType
    get() = when (this) {
        is HttpMonitorRepository -> MonitorType.HTTP_SSL
        is PushMonitorRepository -> MonitorType.PUSH
        is IcmpMonitorRepository -> MonitorType.ICMP
        is TcpMonitorRepository -> MonitorType.TCP
        is DnsMonitorRepository -> MonitorType.DNS
    }

val UptimeEventRepository.monitorType: MonitorType
    get() = when (this) {
        is HttpUptimeEventRepository -> MonitorType.HTTP_SSL
        is PushUptimeEventRepository -> MonitorType.PUSH
        is IcmpUptimeEventRepository -> MonitorType.ICMP
        is TcpUptimeEventRepository -> MonitorType.TCP
        is DnsUptimeEventRepository -> MonitorType.DNS
    }
