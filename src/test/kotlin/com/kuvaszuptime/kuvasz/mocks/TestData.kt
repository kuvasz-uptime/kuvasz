package com.kuvaszuptime.kuvasz.mocks

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.CertificateInfo
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.tables.records.UptimeEventRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jooq.DSLContext
import java.time.OffsetDateTime

@Suppress("LongParameterList")
fun createMonitor(
    repository: MonitorRepository,
    enabled: Boolean = true,
    sslCheckEnabled: Boolean = true,
    uptimeCheckInterval: Int = 30000,
    monitorName: String = "testMonitor",
    url: String = "http://irrelevant.com",
    pagerdutyIntegrationKey: String? = null,
    requestMethod: HttpMethod = HttpMethod.GET,
    latencyHistoryEnabled: Boolean = true,
    forceNoCache: Boolean = true,
    followRedirects: Boolean = true,
): MonitorRecord {
    val monitor = MonitorRecord()
        .setName(monitorName)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setUrl(url)
        .setPagerdutyIntegrationKey(pagerdutyIntegrationKey)
        .setEnabled(enabled)
        .setRequestMethod(requestMethod)
        .setSslCheckEnabled(sslCheckEnabled)
        .setCreatedAt(getCurrentTimestamp())
        .setRequestMethod(requestMethod)
        .setLatencyHistoryEnabled(latencyHistoryEnabled)
        .setForceNoCache(forceNoCache)
        .setFollowRedirects(followRedirects)
    return repository.returningInsert(monitor).orNull().shouldNotBeNull()
}

fun createUptimeEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: UptimeStatus = UptimeStatus.UP,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?
) = dslContext
    .insertInto(UPTIME_EVENT)
    .set(
        UptimeEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(endedAt ?: startedAt)
            .setEndedAt(endedAt)
    )
    .execute()

fun createSSLEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: SslStatus = SslStatus.VALID,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?
) = dslContext
    .insertInto(SSL_EVENT)
    .set(
        SslEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(endedAt ?: startedAt)
            .setEndedAt(endedAt)
    )
    .execute()

fun generateCertificateInfo(validTo: OffsetDateTime = getCurrentTimestamp().plusDays(60)) =
    CertificateInfo(validTo)
