package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.scheduling.annotation.Scheduled
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseCleaner @Inject constructor(
    private val appConfig: AppConfig,
    private val uptimeEventRepository: UptimeEventRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val sslEventRepository: SSLEventRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseCleaner::class.java)
    }

    @Scheduled(cron = "0 2 * * *")
    @Requires(notEnv = [Environment.TEST])
    fun cleanObsoleteData() {
        val limit = getCurrentTimestamp().minusDays(appConfig.dataRetentionDays.toLong())
        val deletedUptimeEvents = uptimeEventRepository.deleteEventsBeforeDate(limit)
        val deletedLatencyLogs = latencyLogRepository.deleteLogsBeforeDate(limit)
        val deletedSSLEvents = sslEventRepository.deleteEventsBeforeDate(limit)

        logger.info("$deletedUptimeEvents UPTIME_EVENT record has been deleted")
        logger.info("$deletedLatencyLogs LATENCY_LOG record has been deleted")
        logger.info("$deletedSSLEvents SSL_EVENT record has been deleted")
    }
}
