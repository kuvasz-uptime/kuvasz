package com.kuvaszuptime.kuvasz.mocks

import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.CertificateInfo
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.tables.pojos.SslEventPojo
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import java.time.OffsetDateTime

@Suppress("LongParameterList")
fun createMonitor(
    repository: MonitorRepository,
    id: Int = 99999,
    enabled: Boolean = true,
    sslCheckEnabled: Boolean = true,
    uptimeCheckInterval: Int = 30000,
    monitorName: String = "testMonitor",
    url: String = "http://irrelevant.com"
): MonitorPojo {
    val monitor = MonitorPojo()
        .setId(id)
        .setName(monitorName)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setUrl(url)
        .setEnabled(enabled)
        .setSslCheckEnabled(sslCheckEnabled)
        .setCreatedAt(getCurrentTimestamp())
    repository.insert(monitor)
    return monitor
}

fun createUptimeEventRecord(
    repository: UptimeEventRepository,
    monitorId: Int,
    status: UptimeStatus = UptimeStatus.UP,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?
) =
    repository.insert(
        UptimeEventPojo()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(endedAt ?: startedAt)
            .setEndedAt(endedAt)
    )

fun createSSLEventRecord(
    repository: SSLEventRepository,
    monitorId: Int,
    status: SslStatus = SslStatus.VALID,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?
) =
    repository.insert(
        SslEventPojo()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(endedAt ?: startedAt)
            .setEndedAt(endedAt)
    )

fun generateCertificateInfo(
    validFrom: OffsetDateTime = getCurrentTimestamp().minusDays(30),
    validTo: OffsetDateTime = getCurrentTimestamp().plusDays(60)
) = CertificateInfo(validFrom, validTo)
