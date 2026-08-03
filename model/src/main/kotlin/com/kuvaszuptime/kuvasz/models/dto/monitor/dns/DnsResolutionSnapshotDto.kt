package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import java.time.OffsetDateTime

data class DnsResolutionSnapshotDto(
    val records: DnsSnapshotRecords,
    val updatedAt: OffsetDateTime,
)

typealias DnsSnapshotRecords = Map<DnsRecordType, List<String>>
