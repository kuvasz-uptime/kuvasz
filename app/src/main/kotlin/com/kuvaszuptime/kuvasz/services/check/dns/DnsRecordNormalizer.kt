package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsSnapshotRecords
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import org.xbill.DNS.Record
import org.xbill.DNS.TXTRecord

/**
 * The outcome of evaluating a monitor's [DnsRecordMatcher]s against a resolved answer set.
 *
 * @param matched true iff every matcher is satisfied (they are ANDed).
 * @param failedMatchers the matchers that were not satisfied, for a human-readable error message.
 */
data class DnsMatchResult(
    val matched: Boolean,
    val failedMatchers: List<DnsRecordMatcher>,
)

/**
 * Canonicalizes DNS records to a stable presentation string and evaluates [DnsRecordMatcher]s against them.
 *
 * The per-type presentation format comes from dnsjava's own [Record.rdataToString] (the same text `dig` prints).
 * TXT is the one exception: its 255-byte character-strings are rejoined
 * into the single logical value (so a >255-byte DKIM key is compared as one string, not as fragments), which
 * `rdataToString` would instead render as separate quoted chunks.
 */
object DnsRecordNormalizer {

    fun normalize(record: Record): String {
        val raw = if (record is TXTRecord) record.strings.joinToString(separator = "") else record.rdataToString()
        return canonicalize(raw)
    }

    fun normalizeValue(raw: String): String = canonicalize(raw)

    /**
     * Evaluates [matchers] against the already-normalized [records]. The assertion passes iff **every** matcher is
     * satisfied; a single matcher is satisfied iff **at least one** normalized record of its type matches.
     */
    fun evaluate(
        matchers: List<DnsRecordMatcher>,
        records: DnsSnapshotRecords,
    ): DnsMatchResult {
        val failed = matchers.filterNot { matcher -> matcher.isSatisfiedBy(records[matcher.recordType].orEmpty()) }
        return DnsMatchResult(matched = failed.isEmpty(), failedMatchers = failed)
    }

    private fun DnsRecordMatcher.isSatisfiedBy(candidates: List<String>): Boolean =
        when (matchType) {
            DnsMatchType.EXACT -> {
                val normalizedValue = normalizeValue(value)
                candidates.any { it == normalizedValue }
            }
            DnsMatchType.CONTAINS -> {
                val normalizedValue = normalizeValue(value)
                candidates.any { it.contains(normalizedValue) }
            }
            DnsMatchType.REGEX -> {
                val regex = Regex(value, RegexOption.IGNORE_CASE)
                candidates.any { regex.containsMatchIn(it) }
            }
        }

    private fun canonicalize(raw: String): String =
        raw.replace("\"", "").trim().replace(WHITESPACE, " ").removeSuffix(".").lowercase()

    private val WHITESPACE = Regex("\\s+")
}
