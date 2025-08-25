package com.kuvaszuptime.kuvasz.mocks

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.HttpUptimeEvent.HTTP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.models.CertificateInfo
import com.kuvaszuptime.kuvasz.models.dto.toJsonNode
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jooq.DSLContext
import java.time.OffsetDateTime
import java.util.UUID

fun createMonitor(
    repository: HttpMonitorRepository,
    enabled: Boolean = true,
    sslCheckEnabled: Boolean = true,
    uptimeCheckInterval: Int = 30000,
    monitorName: String = UUID.randomUUID().toString(),
    url: String = "http://irrelevant.com",
    requestMethod: HttpMethod = HttpMethod.GET,
    latencyHistoryEnabled: Boolean = true,
    forceNoCache: Boolean = true,
    followRedirects: Boolean = true,
    sslExpiryThreshold: Int = 30,
    integrations: List<IntegrationID> = emptyList(),
    expectedStatusCodes: Set<Int> = emptySet(),
    responseTimeThresholdMillis: Int? = null,
    expectedKeyword: String? = null,
    expectedKeywordCaseSensitive: Boolean = false,
    expectedKeywordNegated: Boolean = false,
    requestHeaders: Map<String, String> = emptyMap(),
    expectedHeaders: Map<String, String> = emptyMap(),
    requestBody: String? = null,
): HttpMonitorRecord {
    val monitor = HttpMonitorRecord()
        .setName(monitorName)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setUrl(url)
        .setEnabled(enabled)
        .setRequestMethod(requestMethod)
        .setSslCheckEnabled(sslCheckEnabled)
        .setCreatedAt(getCurrentTimestamp())
        .setRequestMethod(requestMethod)
        .setLatencyHistoryEnabled(latencyHistoryEnabled)
        .setForceNoCache(forceNoCache)
        .setFollowRedirects(followRedirects)
        .setSslExpiryThreshold(sslExpiryThreshold)
        .setIntegrations(integrations.toTypedArray())
        .setExpectedStatusCodes(expectedStatusCodes.toTypedArray())
        .setResponseTimeThresholdMillis(responseTimeThresholdMillis)
        .setExpectedKeyword(expectedKeyword)
        .setExpectedKeywordCaseSensitive(expectedKeywordCaseSensitive)
        .setExpectedKeywordNegated(expectedKeywordNegated)
        .setRequestHeaders(requestHeaders.toJsonNode())
        .setExpectedHeaders(expectedHeaders.toJsonNode())
        .setRequestBody(requestBody)
    return repository.returningInsert(monitor).orNull().shouldNotBeNull()
}

fun createUptimeEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: UptimeStatus = UptimeStatus.UP,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
) = dslContext
    .insertInto(HTTP_UPTIME_EVENT)
    .set(
        HttpUptimeEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(endedAt ?: startedAt)
            .setEndedAt(endedAt)
    )
    .returning(HTTP_UPTIME_EVENT.asterisk())
    .fetchOneOrThrow<HttpUptimeEventRecord>()

fun createSSLEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: SslStatus = SslStatus.VALID,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    sslExpiryDate: OffsetDateTime? = null,
) = dslContext
    .insertInto(SSL_EVENT)
    .set(
        SslEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(endedAt ?: startedAt)
            .setEndedAt(endedAt)
            .setSslExpiryDate(sslExpiryDate)
    )
    .execute()

fun generateCertificateInfo(validTo: OffsetDateTime = getCurrentTimestamp().plusDays(60)) =
    CertificateInfo(validTo)
