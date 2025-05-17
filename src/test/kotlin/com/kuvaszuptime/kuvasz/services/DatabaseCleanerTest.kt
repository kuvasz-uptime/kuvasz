package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createUptimeEventRecord
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.records.LatencyLogRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
@Property(name = "app-config.uptime-data-retention-days", value = "7")
@Property(name = "app-config.latency-data-retention-days", value = "5")
class DatabaseCleanerTest(
    private val uptimeEventRepository: UptimeEventRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val monitorRepository: MonitorRepository,
    private val sslEventRepository: SSLEventRepository,
    private val databaseCleaner: DatabaseCleaner,
) : DatabaseBehaviorSpec() {
    init {

        given("a DatabaseCleaner service") {
            `when`("there is an UPTIME_EVENT record with an end date greater than retention limit") {
                val monitor = createMonitor(monitorRepository)
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(1),
                    endedAt = getCurrentTimestamp()
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val uptimeEventRecords = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 1
                }
            }

            `when`("there is an UPTIME_EVENT record without an end date") {
                val monitor = createMonitor(monitorRepository)
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = null
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val uptimeEventRecords = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 1
                }
            }

            `when`("there is an UPTIME_EVENT record with an end date less than retention limit") {
                val monitor = createMonitor(monitorRepository)
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = getCurrentTimestamp().minusDays(8)
                )
                databaseCleaner.cleanObsoleteData()

                then("it should delete it") {
                    val uptimeEventRecords = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 0
                }
            }

            `when`("there is a LATENCY_LOG record with a creation date greater than retention limit") {
                val monitor = createMonitor(monitorRepository)
                insertLatencyLogRecord(monitor.id, getCurrentTimestamp())
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id) shouldHaveSize 1
                }
            }

            `when`("there is a LATENCY_LOG record with a creation date less than retention limit") {
                val monitor = createMonitor(monitorRepository)
                insertLatencyLogRecord(monitor.id, getCurrentTimestamp().minusDays(6))
                databaseCleaner.cleanObsoleteData()

                then("it should delete it") {
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }

            `when`("there is an SSL_EVENT record with an end date greater than retention limit") {
                val monitor = createMonitor(monitorRepository)
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(1),
                    endedAt = getCurrentTimestamp()
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val sslEventRecords = sslEventRepository.fetchByMonitorId(monitor.id)
                    sslEventRecords shouldHaveSize 1
                }
            }

            `when`("there is an SSL_EVENT record without an end date") {
                val monitor = createMonitor(monitorRepository)
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = null
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val sslEventRecords = sslEventRepository.fetchByMonitorId(monitor.id)
                    sslEventRecords shouldHaveSize 1
                }
            }

            `when`("there is an SSL_EVENT record with an end date less than retention limit") {
                val monitor = createMonitor(monitorRepository)
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = getCurrentTimestamp().minusDays(8)
                )
                databaseCleaner.cleanObsoleteData()

                then("it should delete it") {
                    val sslEventRecords = sslEventRepository.fetchByMonitorId(monitor.id)
                    sslEventRecords shouldHaveSize 0
                }
            }
        }
    }

    private fun insertLatencyLogRecord(monitorId: Long, createdAt: OffsetDateTime) = dslContext
        .insertInto(LATENCY_LOG)
        .set(
            LatencyLogRecord()
                .setMonitorId(monitorId)
                .setLatency(1000)
                .setCreatedAt(createdAt)
        )
        .execute()
}
