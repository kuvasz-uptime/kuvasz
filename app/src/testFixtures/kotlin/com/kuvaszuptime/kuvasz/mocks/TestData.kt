package com.kuvaszuptime.kuvasz.mocks

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DnsMetricsLog.DNS_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.DnsUptimeEvent.DNS_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.HttpUptimeEvent.HTTP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMetricsLog.ICMP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpUptimeEvent.ICMP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.MaintenanceWindow.MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.jooq.tables.PendingFailure.PENDING_FAILURE
import com.kuvaszuptime.kuvasz.jooq.tables.PushUptimeEvent.PUSH_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMetricsLogRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PendingFailureRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.SslEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMetricsLogRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.tables.TcpMetricsLog.TCP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.TcpUptimeEvent.TCP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDefaults
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.dns.toJsonNode
import com.kuvaszuptime.kuvasz.models.monitor.http.toJsonNode
import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import org.jooq.DSLContext
import java.time.OffsetDateTime
import java.util.UUID

fun createHttpMonitor(
    repository: HttpMonitorRepository,
    enabled: Boolean = true,
    sslCheckEnabled: Boolean = true,
    uptimeCheckInterval: Int = 30000,
    monitorName: String = randomClientSecret(),
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
    failureCountThreshold: Long = 1L,
    sensitiveUrl: Boolean = false,
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
        .setFailureCountThreshold(failureCountThreshold)
        .setSensitiveUrl(sensitiveUrl)
    return repository.returningInsert(monitor)
}

fun createPushMonitor(
    repository: PushMonitorRepository,
    enabled: Boolean = true,
    heartbeatInterval: Long = 300,
    gracePeriod: Long = 0,
    clientSecret: String = randomClientSecret(),
    monitorName: String = randomClientSecret(),
    integrations: List<IntegrationID> = emptyList(),
    lastHeartbeat: OffsetDateTime? = null,
    failureCountThreshold: Long = 1L,
): PushMonitorRecord {
    val monitor = PushMonitorRecord()
        .setName(monitorName)
        .setHeartbeatInterval(heartbeatInterval)
        .setGracePeriod(gracePeriod)
        .setClientSecret(clientSecret)
        .setEnabled(enabled)
        .setCreatedAt(getCurrentTimestamp())
        .setIntegrations(integrations.toTypedArray())
        .setLastHeartbeat(lastHeartbeat)
        .setFailureCountThreshold(failureCountThreshold)
    return repository.returningInsert(monitor)
}

fun createPendingFailure(
    dslContext: DSLContext,
    monitorId: Long,
    failureCount: Long = 1,
    updatedAt: OffsetDateTime = getCurrentTimestamp(),
) = dslContext
    .insertInto(PENDING_FAILURE)
    .set(
        PendingFailureRecord()
            .setMonitorId(monitorId)
            .setFailureCount(failureCount)
            .setUpdatedAt(updatedAt)
    )
    .returning(PENDING_FAILURE.asterisk())
    .fetchOneOrThrow<PendingFailureRecord>()

fun createHttpUptimeEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: UptimeStatus = UptimeStatus.UP,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    error: String? = null,
    updatedAt: OffsetDateTime? = null,
) = dslContext
    .insertInto(HTTP_UPTIME_EVENT)
    .set(
        HttpUptimeEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(updatedAt ?: endedAt ?: startedAt)
            .setEndedAt(endedAt)
            .setError(error)
    )
    .returning(HTTP_UPTIME_EVENT.asterisk())
    .fetchOneOrThrow<HttpUptimeEventRecord>()

fun createPushUptimeEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: UptimeStatus = UptimeStatus.UP,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    error: String? = null,
    updatedAt: OffsetDateTime? = null,
) = dslContext
    .insertInto(PUSH_UPTIME_EVENT)
    .set(
        PushUptimeEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(updatedAt ?: endedAt ?: startedAt)
            .setEndedAt(endedAt)
            .setError(error)
    )
    .returning(PUSH_UPTIME_EVENT.asterisk())
    .fetchOneOrThrow<PushUptimeEventRecord>()

fun createSSLEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: SslStatus = SslStatus.VALID,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    sslExpiryDate: OffsetDateTime? = null,
    error: String? = null,
    updatedAt: OffsetDateTime? = null,
) = dslContext
    .insertInto(SSL_EVENT)
    .set(
        SslEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(updatedAt ?: endedAt ?: startedAt)
            .setEndedAt(endedAt)
            .setSslExpiryDate(sslExpiryDate)
            .setError(error)
    )
    .returning(SSL_EVENT.asterisk())
    .fetchOneOrThrow<SslEventRecord>()

fun generateCertificateInfo(validTo: OffsetDateTime = getCurrentTimestamp().plusDays(60)) =
    CertificateInfo(validTo)

fun createStatusPage(
    dslContext: DSLContext,
    title: String = "Status Page",
    slug: String = randomClientSecret(),
    public: Boolean = StatusPageDefaults.CUSTOM_PAGE_PUBLIC,
    monitors: List<MonitorID> = emptyList(),
    customLogoUrl: String? = null,
    customFaviconUrl: String? = null,
) = dslContext
    .insertInto(STATUS_PAGE)
    .set(
        StatusPageRecord()
            .setTitle(title)
            .setSlug(slug)
            .setCustomLogoUrl(customLogoUrl)
            .setCustomFaviconUrl(customFaviconUrl)
            .setPublic(public)
            .setMonitors(monitors.toTypedArray())
    )
    .returning(STATUS_PAGE.asterisk())
    .fetchOneOrThrow<StatusPageRecord>()

@Suppress("LongParameterList")
fun createMaintenanceWindow(
    dslContext: DSLContext,
    name: String = randomClientSecret(),
    description: String? = null,
    enabled: Boolean = true,
    global: Boolean = false,
    showOnStatusPages: Boolean = false,
    cron: String? = null,
    start: OffsetDateTime? = null,
    duration: String? = null,
    monitors: List<MonitorID> = emptyList(),
    integrations: List<IntegrationID> = emptyList(),
): MaintenanceWindowRecord = dslContext
    .insertInto(MAINTENANCE_WINDOW)
    .set(
        MaintenanceWindowRecord()
            .setName(name)
            .setDescription(description)
            .setEnabled(enabled)
            .setGlobal(global)
            .setShowOnStatusPages(showOnStatusPages)
            .setCron(cron)
            .setStart(start)
            .setDuration(duration)
            .setMonitors(monitors.toTypedArray())
            .setIntegrations(integrations.toTypedArray())
    )
    .returning(MAINTENANCE_WINDOW.asterisk())
    .fetchOneOrThrow<MaintenanceWindowRecord>()

fun createIcmpMonitor(
    repository: IcmpMonitorRepository,
    enabled: Boolean = true,
    host: String = "127.0.0.1",
    monitorName: String = randomClientSecret(),
    uptimeCheckInterval: Int = 60,
    packetCount: Int = 3,
    timeoutSeconds: Int = 5,
    packetLossThreshold: Int = 100,
    failureCountThreshold: Long = 1L,
    integrations: List<IntegrationID> = emptyList(),
    metricsHistoryEnabled: Boolean = true,
): IcmpMonitorRecord {
    val monitor = IcmpMonitorRecord()
        .setName(monitorName)
        .setHost(host)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setPacketCount(packetCount)
        .setTimeoutSeconds(timeoutSeconds)
        .setPacketLossThreshold(packetLossThreshold)
        .setFailureCountThreshold(failureCountThreshold)
        .setEnabled(enabled)
        .setCreatedAt(getCurrentTimestamp())
        .setIntegrations(integrations.toTypedArray())
        .setMetricsHistoryEnabled(metricsHistoryEnabled)
    return repository.returningInsert(monitor)
}

fun createTcpMonitor(
    repository: TcpMonitorRepository,
    enabled: Boolean = true,
    host: String = "127.0.0.1",
    port: Int = 8080,
    monitorName: String = randomClientSecret(),
    uptimeCheckInterval: Int = 60,
    timeoutMs: Int = 5000,
    latencyThresholdMs: Int? = null,
    failureCountThreshold: Long = 1L,
    integrations: List<IntegrationID> = emptyList(),
    metricsHistoryEnabled: Boolean = true,
): TcpMonitorRecord {
    val monitor = TcpMonitorRecord()
        .setName(monitorName)
        .setHost(host)
        .setPort(port)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setTimeoutMs(timeoutMs)
        .setLatencyThresholdMs(latencyThresholdMs)
        .setFailureCountThreshold(failureCountThreshold)
        .setEnabled(enabled)
        .setCreatedAt(getCurrentTimestamp())
        .setIntegrations(integrations.toTypedArray())
        .setMetricsHistoryEnabled(metricsHistoryEnabled)
    return repository.returningInsert(monitor)
}

@Suppress("LongParameterList")
fun createDnsMonitor(
    repository: DnsMonitorRepository,
    enabled: Boolean = true,
    host: String = "example.com",
    resolverHost: String? = null,
    resolverPort: Int = 53,
    transport: DnsTransport = DnsTransport.UDP,
    recordMatchers: List<DnsRecordMatcher> = emptyList(),
    expectedResponseCode: DnsResponseCode = DnsResponseCode.NOERROR,
    driftDetectionEnabled: Boolean = false,
    driftRecordTypes: List<DnsRecordType> = emptyList(),
    monitorName: String = randomClientSecret(),
    uptimeCheckInterval: Int = 60,
    timeoutMs: Int = 5000,
    latencyThresholdMs: Int? = null,
    failureCountThreshold: Long = 1L,
    integrations: List<IntegrationID> = emptyList(),
    metricsHistoryEnabled: Boolean = true,
): DnsMonitorRecord {
    val monitor = DnsMonitorRecord()
        .setName(monitorName)
        .setHost(host)
        .setResolverHost(resolverHost)
        .setResolverPort(resolverPort)
        .setTransport(transport)
        .setRecordMatchers(recordMatchers.toJsonNode())
        .setExpectedResponseCode(expectedResponseCode)
        .setDriftDetectionEnabled(driftDetectionEnabled)
        .setDriftRecordTypes(driftRecordTypes.toTypedArray())
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setTimeoutMs(timeoutMs)
        .setLatencyThresholdMs(latencyThresholdMs)
        .setFailureCountThreshold(failureCountThreshold)
        .setEnabled(enabled)
        .setCreatedAt(getCurrentTimestamp())
        .setIntegrations(integrations.toTypedArray())
        .setMetricsHistoryEnabled(metricsHistoryEnabled)
    return repository.returningInsert(monitor)
}

fun createDnsUptimeEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: UptimeStatus = UptimeStatus.UP,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    error: String? = null,
    updatedAt: OffsetDateTime? = null,
) = dslContext
    .insertInto(DNS_UPTIME_EVENT)
    .set(
        DnsUptimeEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(updatedAt ?: endedAt ?: startedAt)
            .setEndedAt(endedAt)
            .setError(error)
    )
    .returning(DNS_UPTIME_EVENT.asterisk())
    .fetchOneOrThrow<DnsUptimeEventRecord>()

fun createDnsMetricsLogRecord(
    dslContext: DSLContext,
    monitorId: Long,
    latencyMs: Int? = 10,
    createdAt: OffsetDateTime = getCurrentTimestamp(),
) = dslContext
    .insertInto(DNS_METRICS_LOG)
    .set(
        DnsMetricsLogRecord()
            .setMonitorId(monitorId)
            .setLatencyMs(latencyMs)
            .setCreatedAt(createdAt)
    )
    .returning(DNS_METRICS_LOG.asterisk())
    .fetchOneOrThrow<DnsMetricsLogRecord>()

fun createTcpUptimeEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: UptimeStatus = UptimeStatus.UP,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    error: String? = null,
    updatedAt: OffsetDateTime? = null,
) = dslContext
    .insertInto(TCP_UPTIME_EVENT)
    .set(
        TcpUptimeEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(updatedAt ?: endedAt ?: startedAt)
            .setEndedAt(endedAt)
            .setError(error)
    )
    .returning(TCP_UPTIME_EVENT.asterisk())
    .fetchOneOrThrow<TcpUptimeEventRecord>()

fun createTcpMetricsLogRecord(
    dslContext: DSLContext,
    monitorId: Long,
    latencyMs: Int? = 10,
    createdAt: OffsetDateTime = getCurrentTimestamp(),
) = dslContext
    .insertInto(TCP_METRICS_LOG)
    .set(
        TcpMetricsLogRecord()
            .setMonitorId(monitorId)
            .setLatencyMs(latencyMs)
            .setCreatedAt(createdAt)
    )
    .returning(TCP_METRICS_LOG.asterisk())
    .fetchOneOrThrow<TcpMetricsLogRecord>()

fun createIcmpUptimeEventRecord(
    dslContext: DSLContext,
    monitorId: Long,
    status: UptimeStatus = UptimeStatus.UP,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    error: String? = null,
    updatedAt: OffsetDateTime? = null,
) = dslContext
    .insertInto(ICMP_UPTIME_EVENT)
    .set(
        IcmpUptimeEventRecord()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(updatedAt ?: endedAt ?: startedAt)
            .setEndedAt(endedAt)
            .setError(error)
    )
    .returning(ICMP_UPTIME_EVENT.asterisk())
    .fetchOneOrThrow<IcmpUptimeEventRecord>()

fun createIcmpMetricsLogRecord(
    dslContext: DSLContext,
    monitorId: Long,
    latencyMs: Int? = 10,
    packetLossPercentage: Int = 0,
    createdAt: OffsetDateTime = getCurrentTimestamp(),
) = dslContext
    .insertInto(ICMP_METRICS_LOG)
    .set(
        IcmpMetricsLogRecord()
            .setMonitorId(monitorId)
            .setLatencyMs(latencyMs)
            .setPacketLossPercentage(packetLossPercentage)
            .setCreatedAt(createdAt)
    )
    .returning(ICMP_METRICS_LOG.asterisk())
    .fetchOneOrThrow<IcmpMetricsLogRecord>()

fun randomClientSecret() = UUID.randomUUID().toString()
