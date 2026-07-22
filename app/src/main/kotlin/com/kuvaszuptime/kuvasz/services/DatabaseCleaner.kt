package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DnsResolutionSnapshotRepository
import com.kuvaszuptime.kuvasz.repositories.DnsUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpUptimeEventRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton

@Singleton
class DatabaseCleaner(
    private val appConfig: AppConfig,
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val icmpUptimeEventRepository: IcmpUptimeEventRepository,
    private val tcpUptimeEventRepository: TcpUptimeEventRepository,
    private val dnsUptimeEventRepository: DnsUptimeEventRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
    private val icmpMetricsLogRepository: IcmpMetricsLogRepository,
    private val tcpMetricsLogRepository: TcpMetricsLogRepository,
    private val dnsMetricsLogRepository: DnsMetricsLogRepository,
    private val dnsResolutionSnapshotRepository: DnsResolutionSnapshotRepository,
    private val sslEventRepository: SSLEventRepository
) {

    companion object {
        private val logger = loggerFor<DatabaseCleaner>()
    }

    @Scheduled(cron = "0 2 * * *")
    @Requires(notEnv = [Environment.TEST])
    fun cleanObsoleteData() {
        val eventLimit = getCurrentTimestamp().minusDays(appConfig.eventDataRetentionDays.toLong())
        val latencyLimit = getCurrentTimestamp().minusDays(appConfig.latencyDataRetentionDays.toLong())

        val deletedHttpUptimeEvents = httpUptimeEventRepository.deleteEventsBeforeDate(eventLimit)
        val deletedPushUptimeEvents = pushUptimeEventRepository.deleteEventsBeforeDate(eventLimit)
        val deletedIcmpUptimeEvents = icmpUptimeEventRepository.deleteEventsBeforeDate(eventLimit)
        val deletedTcpUptimeEvents = tcpUptimeEventRepository.deleteEventsBeforeDate(eventLimit)
        val deletedDnsUptimeEvents = dnsUptimeEventRepository.deleteEventsBeforeDate(eventLimit)
        val deletedSSLEvents = sslEventRepository.deleteEventsBeforeDate(eventLimit)
        val deletedLatencyLogs = latencyLogRepository.deleteLogsBeforeDate(latencyLimit)
        val deletedIcmpMetricsLogs = icmpMetricsLogRepository.deleteLogsBeforeDate(latencyLimit)
        val deletedTcpMetricsLogs = tcpMetricsLogRepository.deleteLogsBeforeDate(latencyLimit)
        val deletedDnsMetricsLogs = dnsMetricsLogRepository.deleteLogsBeforeDate(latencyLimit)
        val deletedDnsSnapshots = dnsResolutionSnapshotRepository.deleteSnapshotsOfDriftDisabledMonitors()

        logger.info("$deletedHttpUptimeEvents HTTP_UPTIME_EVENT record has been deleted")
        logger.info("$deletedPushUptimeEvents PUSH_UPTIME_EVENT record has been deleted")
        logger.info("$deletedIcmpUptimeEvents ICMP_UPTIME_EVENT record has been deleted")
        logger.info("$deletedTcpUptimeEvents TCP_UPTIME_EVENT record has been deleted")
        logger.info("$deletedDnsUptimeEvents DNS_UPTIME_EVENT record has been deleted")
        logger.info("$deletedLatencyLogs LATENCY_LOG record has been deleted")
        logger.info("$deletedIcmpMetricsLogs ICMP_METRICS_LOG record has been deleted")
        logger.info("$deletedTcpMetricsLogs TCP_METRICS_LOG record has been deleted")
        logger.info("$deletedDnsMetricsLogs DNS_METRICS_LOG record has been deleted")
        logger.info("$deletedDnsSnapshots DNS_RESOLUTION_SNAPSHOT record has been deleted")
        logger.info("$deletedSSLEvents SSL_EVENT record has been deleted")
    }
}
