package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.xbill.DNS.ARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.NSRecord
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Resolver
import org.xbill.DNS.Section
import org.xbill.DNS.TXTRecord
import java.io.IOException
import java.net.InetAddress
import java.time.Duration

private fun name(fqdn: String) = Name.fromConstantString(fqdn)

private fun response(rcode: Int, vararg answers: Record): Message = Message().apply {
    header.rcode = rcode
    answers.forEach { addRecord(it, Section.ANSWER) }
}

class DnsResolveExecutorTest : BehaviorSpec({

    fun executorReturning(resolver: Resolver): DnsResolveExecutor {
        val factory = mockk<DnsResolverFactory>()
        every { factory.create(any(), any()) } returns resolver
        return DnsResolveExecutor(factory)
    }

    fun aRecord(ip: String) = ARecord(name("example.com."), DClass.IN, 300, InetAddress.getByName(ip))
    fun cnameRecord(target: String) = CNAMERecord(name("example.com."), DClass.IN, 300, name(target))
    fun nsRecord(target: String) = NSRecord(name("example.com."), DClass.IN, 300, name(target))

    given("a DnsResolveExecutor") {

        `when`("a name resolves with NOERROR") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.NOERROR, aRecord("1.2.3.4"), aRecord("5.6.7.8"))

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it returns the normalized A records, NOERROR and a latency reading") {
                result.records[DnsRecordType.A].shouldNotBeNull().shouldContainExactly("1.2.3.4", "5.6.7.8")
                result.responseCode shouldBe DnsResponseCode.NOERROR
                result.latencyMs.shouldNotBeNull() shouldBeGreaterThanOrEqual 0
                result.error.shouldBeNull()
            }
        }

        `when`("the same RRset comes back in a different order between two responses") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returnsMany listOf(
                response(Rcode.NOERROR, aRecord("1.2.3.4"), aRecord("5.6.7.8"), aRecord("9.9.9.9")),
                response(Rcode.NOERROR, aRecord("9.9.9.9"), aRecord("1.2.3.4"), aRecord("5.6.7.8")),
            )
            val executor = executorReturning(resolver)

            fun resolve() = executor.execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            val first = resolve()
            val second = resolve()

            then("both are sorted into the same canonical answer set, so a rotation cannot look like drift") {
                first.records[DnsRecordType.A].shouldNotBeNull()
                    .shouldContainExactly("1.2.3.4", "5.6.7.8", "9.9.9.9")
                second.records shouldBe first.records
            }
        }

        `when`("the name does not exist") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.NXDOMAIN)

            val result = executorReturning(resolver).execute(
                host = "does-not-exist.example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it reports NXDOMAIN with an empty answer set and no error") {
                result.responseCode shouldBe DnsResponseCode.NXDOMAIN
                result.records[DnsRecordType.A].shouldNotBeNull() shouldBe emptyList()
                result.error.shouldBeNull()
            }
        }

        `when`("the server fails") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.SERVFAIL)

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it maps the rcode to SERVFAIL") {
                result.responseCode shouldBe DnsResponseCode.SERVFAIL
            }
        }

        `when`("resolution times out") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } throws IOException("timed out")

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it reports the error, a null latency and no response code") {
                result.error.shouldNotBeNull() shouldBe "timed out"
                result.latencyMs.shouldBeNull()
                result.responseCode.shouldBeNull()
            }
        }

        `when`("several record types are requested") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returnsMany listOf(
                response(Rcode.NOERROR, aRecord("1.2.3.4")),
                response(Rcode.NOERROR, TXTRecord(name("example.com."), DClass.IN, 300, "v=spf1 ~all")),
            )

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = linkedSetOf(DnsRecordType.A, DnsRecordType.TXT),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it queries and returns the answers for every requested type") {
                verify(exactly = 2) { resolver.send(any()) }
                result.records[DnsRecordType.A].shouldNotBeNull().shouldContainExactly("1.2.3.4")
                result.records[DnsRecordType.TXT].shouldNotBeNull().shouldContainExactly("v=spf1 ~all")
            }
        }

        `when`("drift record types are requested that the matchers do not already cover") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returnsMany listOf(
                response(Rcode.NOERROR, aRecord("1.2.3.4")),
                response(Rcode.NOERROR, nsRecord("ns1.example.com.")),
            )

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = setOf(DnsRecordType.A),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
                driftRecordTypes = setOf(DnsRecordType.A, DnsRecordType.NS),
            )

            then("the drift-only type is looked up as well, and the answer set is complete") {
                verify(exactly = 2) { resolver.send(any()) }
                result.records[DnsRecordType.NS].shouldNotBeNull().shouldContainExactly("ns1.example.com")
                result.driftRecordsComplete shouldBe true
            }
        }

        `when`("the drift record types are already covered by the matchers") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.NOERROR, aRecord("1.2.3.4"))

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = setOf(DnsRecordType.A),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
                driftRecordTypes = setOf(DnsRecordType.A),
            )

            then("no extra lookup is made") {
                verify(exactly = 1) { resolver.send(any()) }
                result.driftRecordsComplete shouldBe true
            }
        }

        `when`("a drift-only lookup fails") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returnsMany listOf(
                response(Rcode.NOERROR, aRecord("1.2.3.4")),
            ) andThenThrows IOException("timed out")

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = setOf(DnsRecordType.A),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
                driftRecordTypes = setOf(DnsRecordType.MX),
            )

            then("the check itself still succeeds, but the answer set is flagged as incomplete") {
                result.error.shouldBeNull()
                result.responseCode shouldBe DnsResponseCode.NOERROR
                result.records[DnsRecordType.A].shouldNotBeNull().shouldContainExactly("1.2.3.4")
                result.driftRecordsComplete shouldBe false
            }
        }

        `when`("a drift-only lookup answers with a non-NOERROR response code") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returnsMany listOf(
                response(Rcode.NOERROR, aRecord("1.2.3.4")),
                response(Rcode.SERVFAIL),
            )

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = setOf(DnsRecordType.A),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
                driftRecordTypes = setOf(DnsRecordType.MX),
            )

            then("its empty answer is not recorded as a removal, and the uptime response code is untouched") {
                result.responseCode shouldBe DnsResponseCode.NOERROR
                result.records.containsKey(DnsRecordType.MX) shouldBe false
                result.driftRecordsComplete shouldBe false
            }
        }

        `when`("the transport is TCP") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.NOERROR, aRecord("1.2.3.4"))
            val factory = mockk<DnsResolverFactory>()
            every { factory.create(any(), any()) } returns resolver

            DnsResolveExecutor(factory).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.TCP,
                timeoutMs = 5000,
            )

            then("it asks the factory for a resolver with TCP transport") {
                verify { factory.create(null, DnsTransport.TCP) }
            }
        }

        `when`("a custom resolver port is configured") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.NOERROR, aRecord("1.2.3.4"))

            executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = "9.9.9.9",
                resolverPort = 5353,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it applies the port to the resolver") {
                verify { resolver.setPort(5353) }
            }
        }

        `when`("the answer section carries records of other types (e.g. a CNAME chain)") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns
                response(Rcode.NOERROR, cnameRecord("cdn.example.net."), aRecord("1.2.3.4"))

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("only records of the queried type are kept") {
                result.records[DnsRecordType.A].shouldNotBeNull().shouldContainExactly("1.2.3.4")
            }
        }

        `when`("the server refuses the query") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.REFUSED)

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it maps the rcode to REFUSED") {
                result.responseCode shouldBe DnsResponseCode.REFUSED
            }
        }

        `when`("the server returns an rcode outside the modelled set (e.g. NOTIMP)") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.NOTIMP)

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it collapses to SERVFAIL") {
                result.responseCode shouldBe DnsResponseCode.SERVFAIL
            }
        }

        `when`("different queries return different non-NOERROR codes") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returnsMany listOf(
                response(Rcode.SERVFAIL),
                response(Rcode.NXDOMAIN),
            )

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = linkedSetOf(DnsRecordType.A, DnsRecordType.MX),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("the first non-NOERROR code is reported") {
                result.responseCode shouldBe DnsResponseCode.SERVFAIL
            }
        }

        `when`("an earlier query is NOERROR and a later one is NXDOMAIN") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returnsMany listOf(
                response(Rcode.NOERROR, aRecord("1.2.3.4")),
                response(Rcode.NXDOMAIN),
            )

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = linkedSetOf(DnsRecordType.A, DnsRecordType.MX),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("the problematic code surfaces over the NOERROR one") {
                result.responseCode shouldBe DnsResponseCode.NXDOMAIN
            }
        }

        `when`("a later query fails after an earlier one succeeded") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns
                response(Rcode.NOERROR, aRecord("1.2.3.4")) andThenThrows IOException("boom")

            val result = executorReturning(resolver).execute(
                host = "example.com",
                recordTypes = linkedSetOf(DnsRecordType.A, DnsRecordType.MX),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it fails the whole check but keeps the answers gathered so far") {
                result.error.shouldNotBeNull() shouldBe "boom"
                result.responseCode.shouldBeNull()
                result.latencyMs.shouldBeNull()
                result.records[DnsRecordType.A].shouldNotBeNull().shouldContainExactly("1.2.3.4")
            }
        }

        `when`("the transport is UDP") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.NOERROR, aRecord("1.2.3.4"))
            val factory = mockk<DnsResolverFactory>()
            every { factory.create(any(), any()) } returns resolver

            DnsResolveExecutor(factory).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it asks the factory for a resolver with UDP transport") {
                verify { factory.create(null, DnsTransport.UDP) }
            }
        }

        `when`("a custom resolver host and timeout are configured") {
            val resolver = mockk<Resolver>(relaxed = true)
            every { resolver.send(any()) } returns response(Rcode.NOERROR, aRecord("1.2.3.4"))
            val factory = mockk<DnsResolverFactory>()
            every { factory.create(any(), any()) } returns resolver

            DnsResolveExecutor(factory).execute(
                host = "example.com",
                recordTypes = emptySet(),
                resolverHost = "9.9.9.9",
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 2000,
            )

            then("the factory builds a resolver for that host and transport and the timeout is applied") {
                verify { factory.create("9.9.9.9", DnsTransport.UDP) }
                verify { resolver.setTimeout(Duration.ofMillis(2000)) }
            }
        }
    }
})

@Ignored
class DnsResolveExecutorLiveTest : BehaviorSpec({

    val executor = DnsResolveExecutor(DnsResolverFactory())

    given("real DNS resolution over the system resolver") {

        `when`("resolving a stable A name") {
            val result = executor.execute(
                host = "one.one.one.one",
                recordTypes = emptySet(),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("it returns Cloudflare's well-known address with NOERROR and a latency reading") {
                result.error.shouldBeNull()
                result.responseCode shouldBe DnsResponseCode.NOERROR
                result.records[DnsRecordType.A].shouldNotBeNull() shouldContain "1.1.1.1"
                result.latencyMs.shouldNotBeNull() shouldBeGreaterThanOrEqual 0
            }
        }

        `when`("resolving TXT for a name that publishes an SPF record") {
            val result = executor.execute(
                host = "cloudflare.com",
                recordTypes = setOf(DnsRecordType.TXT),
                resolverHost = null,
                resolverPort = 53,
                transport = DnsTransport.UDP,
                timeoutMs = 5000,
            )

            then("an SPF record is present among the TXT answers") {
                val txtRecords = result.records[DnsRecordType.TXT].shouldNotBeNull()
                txtRecords.any { it.contains("v=spf1") }.shouldBeTrue()
            }
        }
        // There is deliberately no live NXDOMAIN assertion here. NXDOMAIN cannot be observed reliably over live
        // DNS - resolvers, captive portals and transparent :53 proxies commonly rewrite it into a synthesized NOERROR
        // ("NXDOMAIN hijacking"), which you cannot route around.
    }

    given("real DNS resolution against a custom resolver over forced TCP") {

        `when`("querying Cloudflare's resolver directly") {
            val result = executor.execute(
                host = "one.one.one.one",
                recordTypes = setOf(DnsRecordType.A),
                resolverHost = "1.1.1.1",
                resolverPort = 53,
                transport = DnsTransport.TCP,
                timeoutMs = 5000,
            )

            then("it still resolves to the well-known address") {
                result.error.shouldBeNull()
                result.records[DnsRecordType.A].shouldNotBeNull() shouldContain "1.1.1.1"
            }
        }
    }
})
