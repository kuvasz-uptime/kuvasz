package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.util.elapsedMsSince
import com.kuvaszuptime.kuvasz.util.loggerFor
import jakarta.inject.Singleton
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Resolver
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.io.IOException
import java.time.Duration

/**
 * The result of resolving a name for a DNS monitor.
 *
 * @param records the normalized answer records, keyed by the queried [DnsRecordType]. The values are sorted: the order
 * of the records within an RRset is not significant, and resolvers routinely rotate it between responses (round-robin),
 * so a stable order is what makes two answer sets comparable for drift detection.
 * @param responseCode the (first non-NOERROR, else NOERROR) response code observed across the *assertion* queries, or
 * null when resolution failed before any response was received.
 * @param latencyMs wall-clock latency across the *assertion* queries, or null when resolution failed. Drift-only
 * lookups are deliberately excluded so that widening the drift watch list cannot inflate the latency threshold or the
 * stored metrics history.
 * @param error a human-readable error when resolution failed (timeout, network error, malformed name), else null.
 * @param driftRecordsComplete false when a drift-only lookup failed, leaving [records] without an entry that the
 * monitor watches. Comparing a partial answer set would report the missing type as a removal, so drift detection is
 * skipped for the round instead. Always true when no drift-only lookups were requested.
 */
data class DnsCheckResult(
    val records: Map<DnsRecordType, List<String>>,
    val responseCode: DnsResponseCode?,
    val latencyMs: Int?,
    val error: String?,
    val driftRecordsComplete: Boolean = true,
)

@Singleton
class DnsResolverFactory {
    fun create(resolverHost: String?, transport: DnsTransport): Resolver = when (transport) {
        DnsTransport.UDP -> simpleResolver(resolverHost).apply { tcp = false }
        DnsTransport.TCP -> simpleResolver(resolverHost).apply { tcp = true }
    }

    private fun simpleResolver(resolverHost: String?): SimpleResolver =
        if (resolverHost != null) SimpleResolver(resolverHost) else SimpleResolver()
}

@Singleton
class DnsResolveExecutor(private val resolverFactory: DnsResolverFactory) {

    fun execute(
        host: String,
        recordTypes: Set<DnsRecordType>,
        resolverHost: String?,
        resolverPort: Int,
        transport: DnsTransport,
        timeoutMs: Int,
        driftRecordTypes: Set<DnsRecordType> = emptySet(),
    ): DnsCheckResult {
        val assertionTypes = recordTypes.ifEmpty { setOf(DnsRecordType.A) }
        val driftOnlyTypes = driftRecordTypes - assertionTypes

        val resolver = resolverFactory.create(resolverHost, transport).apply {
            setPort(resolverPort)
            timeout = Duration.ofMillis(timeoutMs.toLong())
        }

        val records = mutableMapOf<DnsRecordType, List<String>>()
        var problemResponseCode: DnsResponseCode? = null
        val start = System.nanoTime()

        assertionTypes.forEach { type ->
            val response = try {
                resolver.send(queryFor(host, type.toDnsJavaType()))
            } catch (ex: IOException) {
                return DnsCheckResult(
                    records = records,
                    responseCode = null,
                    latencyMs = null,
                    error = ex.message ?: ex.javaClass.simpleName,
                )
            }
            val rcode = response.rcode.toDnsResponseCode()
            if (problemResponseCode == null && rcode != DnsResponseCode.NOERROR) {
                problemResponseCode = rcode
            }
            records[type] = response.answersOf(type)
        }

        val latencyMs = elapsedMsSince(start)

        // Drift-only lookups are informational: a failure here must not flip the monitor to DOWN, it only means the
        // answer set is incomplete and cannot be compared this round.
        var driftRecordsComplete = true
        driftOnlyTypes.forEach { type ->
            try {
                val response = resolver.send(queryFor(host, type.toDnsJavaType()))
                // A non-NOERROR answer is empty for the same reason a non-existent record is, so it cannot be told
                // apart from "this type is genuinely absent" -- treat it as missing data rather than as a removal.
                if (response.rcode.toDnsResponseCode() == DnsResponseCode.NOERROR) {
                    records[type] = response.answersOf(type)
                } else {
                    driftRecordsComplete = false
                }
            } catch (ex: IOException) {
                logger.debug("Drift lookup of $type for [$host] failed, skipping drift detection this round", ex)
                driftRecordsComplete = false
            }
        }

        return DnsCheckResult(
            records = records,
            responseCode = problemResponseCode ?: DnsResponseCode.NOERROR,
            latencyMs = latencyMs,
            error = null,
            driftRecordsComplete = driftRecordsComplete,
        )
    }

    private fun Message.answersOf(type: DnsRecordType): List<String> {
        val dnsType = type.toDnsJavaType()
        return getSection(Section.ANSWER)
            .filter { it.type == dnsType }
            .map { DnsRecordNormalizer.normalize(it) }
            .sorted()
    }

    private fun queryFor(host: String, dnsType: Int): Message =
        Message.newQuery(
            Record.newRecord(
                Name.fromString(host, Name.root),
                dnsType,
                DClass.IN,
            )
        )

    companion object {
        private val logger = loggerFor<DnsResolveExecutor>()
    }
}

internal fun DnsRecordType.toDnsJavaType(): Int = when (this) {
    DnsRecordType.A -> Type.A
    DnsRecordType.AAAA -> Type.AAAA
    DnsRecordType.CNAME -> Type.CNAME
    DnsRecordType.MX -> Type.MX
    DnsRecordType.NS -> Type.NS
    DnsRecordType.TXT -> Type.TXT
    DnsRecordType.SOA -> Type.SOA
    DnsRecordType.SRV -> Type.SRV
    DnsRecordType.CAA -> Type.CAA
    DnsRecordType.PTR -> Type.PTR
}

internal fun Int.toDnsResponseCode(): DnsResponseCode = when (this) {
    Rcode.NOERROR -> DnsResponseCode.NOERROR
    Rcode.NXDOMAIN -> DnsResponseCode.NXDOMAIN
    Rcode.REFUSED -> DnsResponseCode.REFUSED
    // Every other rcode (SERVFAIL, NOTIMP, FORMERR, ...) is a server-side failure from the monitor's perspective.
    else -> DnsResponseCode.SERVFAIL
}
