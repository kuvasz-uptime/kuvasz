package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.DnsResolutionSnapshot.DNS_RESOLUTION_SNAPSHOT
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.loggerFor
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.JSONB
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.jacksonTypeRef

@Singleton
class DnsResolutionSnapshotRepository(private val dslContext: DSLContext) {

    private val objectMapper = jacksonObjectMapper()
    private val recordsTypeRef = jacksonTypeRef<Map<DnsRecordType, List<String>>>()

    /**
     * Returns null both when there is no snapshot and when the stored one cannot be read, most plausibly a payload
     * shaped by a version the instance has since been downgraded from. The snapshot is disposable state the next
     * check rebuilds for free, so refusing to parse it would take the monitor's uptime checking down forever to
     * protect data we are happy to discard.
     */
    fun getRecords(monitorId: Long): Map<DnsRecordType, List<String>>? =
        dslContext
            .select(DNS_RESOLUTION_SNAPSHOT.RECORDS)
            .from(DNS_RESOLUTION_SNAPSHOT)
            .where(DNS_RESOLUTION_SNAPSHOT.MONITOR_ID.eq(monitorId))
            .fetchOne(DNS_RESOLUTION_SNAPSHOT.RECORDS)
            ?.let { stored ->
                try {
                    objectMapper.readValue(stored.data(), recordsTypeRef)
                } catch (ex: JacksonException) {
                    logger.warn(
                        "The stored DNS resolution snapshot of monitor with ID $monitorId cannot be read, " +
                            "it will be re-seeded on this check: ${ex.message}"
                    )
                    null
                }
            }

    fun upsert(monitorId: Long, records: Map<DnsRecordType, List<String>>) {
        val json = JSONB.valueOf(objectMapper.writeValueAsString(records))
        val now = getCurrentTimestamp()
        dslContext
            .insertInto(DNS_RESOLUTION_SNAPSHOT)
            .set(DNS_RESOLUTION_SNAPSHOT.MONITOR_ID, monitorId)
            .set(DNS_RESOLUTION_SNAPSHOT.RECORDS, json)
            .set(DNS_RESOLUTION_SNAPSHOT.UPDATED_AT, now)
            .onConflict(DNS_RESOLUTION_SNAPSHOT.MONITOR_ID)
            .doUpdate()
            .set(DNS_RESOLUTION_SNAPSHOT.RECORDS, json)
            .set(DNS_RESOLUTION_SNAPSHOT.UPDATED_AT, now)
            .execute()
    }

    companion object {
        private val logger = loggerFor<DnsResolutionSnapshotRepository>()
    }
}
