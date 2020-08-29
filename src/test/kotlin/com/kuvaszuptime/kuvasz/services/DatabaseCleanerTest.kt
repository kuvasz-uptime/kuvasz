package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.pojos.LatencyLogPojo
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldHaveSize
import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest
@Property(name = "app-config.data-retention-days", value = "7")
class DatabaseCleanerTest(
    private val uptimeEventRepository: UptimeEventRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val monitorRepository: MonitorRepository,
    private val databaseCleaner: DatabaseCleaner
) : DatabaseBehaviorSpec() {
    init {

        given("a DatabaseCleaner service") {
            `when`("there is an UPTIME_EVENT record with an end date greater than retention limit") {
                val monitor = createMonitor(monitorRepository)
                insertUptimeEventRecord(monitor.id, getCurrentTimestamp().minusDays(1), getCurrentTimestamp())
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val uptimeEventRecords = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 1
                }
            }

            `when`("there is an UPTIME_EVENT record without an end date") {
                val monitor = createMonitor(monitorRepository)
                insertUptimeEventRecord(monitor.id, getCurrentTimestamp().minusDays(20), null)
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val uptimeEventRecords = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 1
                }
            }

            `when`("there is an UPTIME_EVENT record with an end date less than retention limit") {
                val monitor = createMonitor(monitorRepository)
                insertUptimeEventRecord(
                    monitor.id,
                    getCurrentTimestamp().minusDays(20),
                    getCurrentTimestamp().minusDays(8)
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
                    val latencyLogRecords = latencyLogRepository.fetchByMonitorId(monitor.id)
                    latencyLogRecords shouldHaveSize 1
                }
            }

            `when`("there is a LATENCY_LOG record with a creation date less than retention limit") {
                val monitor = createMonitor(monitorRepository)
                insertLatencyLogRecord(monitor.id, getCurrentTimestamp().minusDays(8))
                databaseCleaner.cleanObsoleteData()

                then("it should delete it") {
                    val latencyLogRecords = latencyLogRepository.fetchByMonitorId(monitor.id)
                    latencyLogRecords shouldHaveSize 0
                }
            }
        }
    }

    private fun insertUptimeEventRecord(monitorId: Int, startedAt: OffsetDateTime, endedAt: OffsetDateTime?) =
        uptimeEventRepository.insert(
            UptimeEventPojo()
                .setMonitorId(monitorId)
                .setStatus(UptimeStatus.UP)
                .setStartedAt(startedAt)
                .setUpdatedAt(endedAt ?: startedAt)
                .setEndedAt(endedAt)
        )

    private fun insertLatencyLogRecord(monitorId: Int, createdAt: OffsetDateTime) =
        latencyLogRepository.insert(
            LatencyLogPojo()
                .setMonitorId(monitorId)
                .setLatency(1000)
                .setCreatedAt(createdAt)
        )
}
