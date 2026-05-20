package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.Tables.HTTP_LATENCY_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpLatencyLogRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
@Property(name = "app-config.event-data-retention-days", value = "7")
@Property(name = "app-config.latency-data-retention-days", value = "5")
class DatabaseCleanerTest(
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val icmpUptimeEventRepository: IcmpUptimeEventRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
    private val icmpMetricsLogRepository: IcmpMetricsLogRepository,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val sslEventRepository: SSLEventRepository,
    private val databaseCleaner: DatabaseCleaner,
) : DatabaseBehaviorSpec() {
    init {

        given("a DatabaseCleaner service") {
            `when`("there is an HTTP_UPTIME_EVENT record with an end date greater than retention limit") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(1),
                    endedAt = getCurrentTimestamp()
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val uptimeEventRecords = httpUptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 1
                }
            }

            `when`("there is an HTTP_UPTIME_EVENT record without an end date") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = null
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val uptimeEventRecords = httpUptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 1
                }
            }

            `when`("there is an HTTP_UPTIME_EVENT record with an end date less than retention limit") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = getCurrentTimestamp().minusDays(8)
                )
                databaseCleaner.cleanObsoleteData()

                then("it should delete it") {
                    val uptimeEventRecords = httpUptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 0
                }
            }

            `when`("there is a PUSH_UPTIME_EVENT record with an end date greater than retention limit") {
                val monitor = createPushMonitor(pushMonitorRepository)
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(1),
                    endedAt = getCurrentTimestamp()
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val uptimeEventRecords = pushUptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 1
                }
            }

            `when`("there is a PUSH_UPTIME_EVENT record without an end date") {
                val monitor = createPushMonitor(pushMonitorRepository)
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = null
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    val uptimeEventRecords = pushUptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 1
                }
            }

            `when`("there is a PUSH_UPTIME_EVENT record with an end date less than retention limit") {
                val monitor = createPushMonitor(pushMonitorRepository)
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = getCurrentTimestamp().minusDays(8)
                )
                databaseCleaner.cleanObsoleteData()

                then("it should delete it") {
                    val uptimeEventRecords = pushUptimeEventRepository.fetchByMonitorId(monitor.id)
                    uptimeEventRecords shouldHaveSize 0
                }
            }

            `when`("there is a LATENCY_LOG record with a creation date greater than retention limit") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                insertLatencyLogRecord(monitor.id, getCurrentTimestamp())
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id) shouldHaveSize 1
                }
            }

            `when`("there is a LATENCY_LOG record with a creation date less than retention limit") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                insertLatencyLogRecord(monitor.id, getCurrentTimestamp().minusDays(6))
                databaseCleaner.cleanObsoleteData()

                then("it should delete it") {
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }

            `when`("there is an SSL_EVENT record with an end date greater than retention limit") {
                val monitor = createHttpMonitor(httpMonitorRepository)
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
                val monitor = createHttpMonitor(httpMonitorRepository)
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
                val monitor = createHttpMonitor(httpMonitorRepository)
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

            `when`("there is an ICMP_UPTIME_EVENT record with an end date greater than retention limit") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(1),
                    endedAt = getCurrentTimestamp()
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    icmpUptimeEventRepository.fetchByMonitorId(monitor.id) shouldHaveSize 1
                }
            }

            `when`("there is an ICMP_UPTIME_EVENT record without an end date") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = null
                )
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    icmpUptimeEventRepository.fetchByMonitorId(monitor.id) shouldHaveSize 1
                }
            }

            `when`("there is an ICMP_UPTIME_EVENT record with an end date less than retention limit") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = getCurrentTimestamp().minusDays(8)
                )
                databaseCleaner.cleanObsoleteData()

                then("it should delete it") {
                    icmpUptimeEventRepository.fetchByMonitorId(monitor.id) shouldHaveSize 0
                }
            }

            `when`("there is an ICMP_METRICS_LOG record with a creation date greater than retention limit") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                createIcmpMetricsLogRecord(dslContext, monitorId = monitor.id, createdAt = getCurrentTimestamp())
                databaseCleaner.cleanObsoleteData()

                then("it should not delete it") {
                    icmpMetricsLogRepository.fetchLatestByMonitorId(monitor.id) shouldHaveSize 1
                }
            }

            `when`("there is an ICMP_METRICS_LOG record with a creation date less than retention limit") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    createdAt = getCurrentTimestamp().minusDays(6)
                )
                databaseCleaner.cleanObsoleteData()

                then("it should delete it") {
                    icmpMetricsLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }
        }
    }

    private fun insertLatencyLogRecord(monitorId: Long, createdAt: OffsetDateTime) = dslContext
        .insertInto(HTTP_LATENCY_LOG)
        .set(
            HttpLatencyLogRecord()
                .setMonitorId(monitorId)
                .setLatency(1000)
                .setCreatedAt(createdAt)
        )
        .execute()
}
