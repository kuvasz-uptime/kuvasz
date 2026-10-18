package com.kuvaszuptime.kuvasz.models.pagination

import com.kuvaszuptime.kuvasz.models.dto.event.EventSortField
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentSortField
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowSortField
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageSortField
import java.time.Duration

object ListDefaults {
    private const val INCIDENT_PERIOD_DAYS = 7L

    val HTTP_MONITOR_SORTING = Sorting(HttpMonitorSortField.NAME, SortOrder.ASC)
    val PUSH_MONITOR_SORTING = Sorting(PushMonitorSortField.NAME, SortOrder.ASC)
    val ICMP_MONITOR_SORTING = Sorting(IcmpMonitorSortField.NAME, SortOrder.ASC)
    val TCP_MONITOR_SORTING = Sorting(TcpMonitorSortField.NAME, SortOrder.ASC)
    val DNS_MONITOR_SORTING = Sorting(DnsMonitorSortField.NAME, SortOrder.ASC)
    val DOCKER_MONITOR_SORTING = Sorting(DockerMonitorSortField.NAME, SortOrder.ASC)
    val EVENT_SORTING = Sorting(EventSortField.STARTED_AT, SortOrder.DESC)
    val STATUS_PAGE_SORTING = Sorting(StatusPageSortField.TITLE, SortOrder.ASC)
    val MAINTENANCE_WINDOW_SORTING = Sorting(MaintenanceWindowSortField.NAME, SortOrder.ASC)

    val INCIDENT_SORTING = Sorting(IncidentSortField.STARTED_AT, SortOrder.DESC)
    val INCIDENT_PERIOD: Duration = Duration.ofDays(INCIDENT_PERIOD_DAYS)
    const val INCIDENT_INCLUDE_RESOLVED = true
}
