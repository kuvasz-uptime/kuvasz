package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.util.elapsedMsSince
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
 * @param records the normalized answer records, keyed by the queried [DnsRecordType].
 * @param responseCode the (first non-NOERROR, else NOERROR) response code observed across the queries, or null when
 * resolution failed before any response was received.
 * @param latencyMs total wall-clock latency across every query in the check, or null when resolution failed.
 * @param error a human-readable error when resolution failed (timeout, network error, malformed name), else null.
 */
data class DnsCheckResult(
    val records: Map<DnsRecordType, List<String>>,
    val responseCode: DnsResponseCode?,
    val latencyMs: Int?,
    val error: String?,
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
    ): DnsCheckResult {
        val typesToQuery = recordTypes.ifEmpty { setOf(DnsRecordType.A) }

        val resolver = resolverFactory.create(resolverHost, transport).apply {
            setPort(resolverPort)
            timeout = Duration.ofMillis(timeoutMs.toLong())
        }

        val records = mutableMapOf<DnsRecordType, List<String>>()
        var problemResponseCode: DnsResponseCode? = null
        val start = System.nanoTime()

        typesToQuery.forEach { type ->
            val dnsType = type.toDnsJavaType()
            val response = try {
                resolver.send(queryFor(host, dnsType))
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
            records[type] = response.getSection(Section.ANSWER)
                .filter { it.type == dnsType }
                .map { DnsRecordNormalizer.normalize(it) }
        }

        return DnsCheckResult(
            records = records,
            responseCode = problemResponseCode ?: DnsResponseCode.NOERROR,
            latencyMs = elapsedMsSince(start),
            error = null,
        )
    }

    private fun queryFor(host: String, dnsType: Int): Message =
        Message.newQuery(
            Record.newRecord(
                Name.fromString(host, Name.root),
                dnsType,
                DClass.IN,
            )
        )
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
