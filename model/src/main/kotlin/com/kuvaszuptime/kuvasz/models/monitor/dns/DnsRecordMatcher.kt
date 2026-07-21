package com.kuvaszuptime.kuvasz.models.monitor.dns

import io.micronaut.core.annotation.Introspected

enum class DnsRecordType { A, AAAA, CNAME, MX, NS, TXT, SOA, SRV, CAA, PTR }

enum class DnsMatchType { EXACT, CONTAINS, REGEX }

/**
 * A single DNS assertion. A matcher is satisfied if **at least one** normalized record of [recordType] in the answer
 * satisfies the comparison described by [matchType] against [value]. All matchers configured on a monitor are ANDed.
 */
@Introspected
data class DnsRecordMatcher(
    val recordType: DnsRecordType,
    val matchType: DnsMatchType = DnsMatchType.CONTAINS,
    val value: String,
)
