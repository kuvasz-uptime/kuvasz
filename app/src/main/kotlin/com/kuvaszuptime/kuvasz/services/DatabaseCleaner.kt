package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
class DatabaseCleaner(
    private val appConfig: AppConfig,
    private val uptimeEventRepository: HttpUptimeEventRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
    private val sslEventRepository: SSLEventRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseCleaner::class.java)
    }

    @Scheduled(cron = "0 2 * * *")
    @Requires(notEnv = [Environment.TEST])
    fun cleanObsoleteData() {
        val eventLimit = getCurrentTimestamp().minusDays(appConfig.eventDataRetentionDays.toLong())
        val latencyLimit = getCurrentTimestamp().minusDays(appConfig.latencyDataRetentionDays.toLong())

        val deletedUptimeEvents = uptimeEventRepository.deleteEventsBeforeDate(eventLimit)
        val deletedSSLEvents = sslEventRepository.deleteEventsBeforeDate(eventLimit)
        val deletedLatencyLogs = latencyLogRepository.deleteLogsBeforeDate(latencyLimit)

        logger.info("$deletedUptimeEvents HTTP_UPTIME_EVENT record has been deleted")
        logger.info("$deletedLatencyLogs LATENCY_LOG record has been deleted")
        logger.info("$deletedSSLEvents SSL_EVENT record has been deleted")
    }
}
