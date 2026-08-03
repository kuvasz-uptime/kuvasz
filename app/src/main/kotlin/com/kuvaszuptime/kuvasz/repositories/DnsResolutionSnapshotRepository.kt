package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.JsonNodeToRecordMapConverter
import com.kuvaszuptime.kuvasz.jooq.tables.DnsResolutionSnapshot.DNS_RESOLUTION_SNAPSHOT
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsResolutionSnapshotDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsSnapshotRecords
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class DnsResolutionSnapshotRepository(private val dslContext: DSLContext) {

    private val recordsConverter = JsonNodeToRecordMapConverter()

    fun getSnapshot(monitorId: Long): DnsResolutionSnapshotDto? =
        dslContext
            .select(
                DNS_RESOLUTION_SNAPSHOT.RECORDS.convert(recordsConverter),
                DNS_RESOLUTION_SNAPSHOT.UPDATED_AT,
            )
            .from(DNS_RESOLUTION_SNAPSHOT)
            .where(DNS_RESOLUTION_SNAPSHOT.MONITOR_ID.eq(monitorId))
            .fetchOneInto(DnsResolutionSnapshotDto::class.java)

    fun upsert(monitorId: Long, records: DnsSnapshotRecords) {
        val recordsJson = recordsConverter.to(records)
        val now = getCurrentTimestamp()
        dslContext
            .insertInto(DNS_RESOLUTION_SNAPSHOT)
            .set(DNS_RESOLUTION_SNAPSHOT.MONITOR_ID, monitorId)
            .set(DNS_RESOLUTION_SNAPSHOT.RECORDS, recordsJson)
            .set(DNS_RESOLUTION_SNAPSHOT.UPDATED_AT, now)
            .onConflict(DNS_RESOLUTION_SNAPSHOT.MONITOR_ID)
            .doUpdate()
            .set(DNS_RESOLUTION_SNAPSHOT.RECORDS, recordsJson)
            .set(DNS_RESOLUTION_SNAPSHOT.UPDATED_AT, now)
            .execute()
    }
}
