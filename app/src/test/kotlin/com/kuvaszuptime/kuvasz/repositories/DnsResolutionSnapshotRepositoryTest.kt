package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class DnsResolutionSnapshotRepositoryTest(
    private val snapshotRepository: DnsResolutionSnapshotRepository,
    private val monitorRepository: DnsMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("a DNS monitor with a recorded resolution snapshot") {
            val monitor = createDnsMonitor(monitorRepository, driftDetectionEnabled = true)
            val records = mapOf(
                DnsRecordType.A to listOf("1.2.3.4", "5.6.7.8"),
                DnsRecordType.MX to listOf("10 mail.example.com"),
            )
            snapshotRepository.upsert(monitor.id, records)

            `when`("getSnapshot is called") {
                val snapshot = snapshotRepository.getSnapshot(monitor.id)

                then("it returns the stored records together with the updated-at timestamp") {
                    snapshot.shouldNotBeNull().records shouldContainExactly records
                    snapshot.updatedAt.shouldNotBeNull()
                }
            }
        }

        given("a DNS monitor without any recorded snapshot") {
            val monitor = createDnsMonitor(monitorRepository)

            `when`("getSnapshot is called") {
                then("it returns null") {
                    snapshotRepository.getSnapshot(monitor.id).shouldBeNull()
                }
            }
        }
    }
}
