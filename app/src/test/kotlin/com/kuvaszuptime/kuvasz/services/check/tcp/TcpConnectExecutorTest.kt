package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import com.kuvaszuptime.kuvasz.services.network.BoundedHostnameResolver
import com.kuvaszuptime.kuvasz.services.network.HostnameResolver
import com.kuvaszuptime.kuvasz.services.network.SystemHostnameResolver
import com.kuvaszuptime.kuvasz.services.proxy.ConfiguredProxy
import com.kuvaszuptime.kuvasz.services.proxy.ProxyCredentials
import com.kuvaszuptime.kuvasz.services.proxy.ProxyTunnel
import com.kuvaszuptime.kuvasz.testutils.TestConnectProxy
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.mockserver.integration.ClientAndServer
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class TcpConnectExecutorTest : BehaviorSpec({

    val resolver = BoundedHostnameResolver(SystemHostnameResolver())
    val executor = TcpConnectExecutor(resolver, ProxyTunnel(resolver))

    lateinit var mockServer: ClientAndServer

    beforeSpec {
        mockServer = ClientAndServer.startClientAndServer(0)
    }

    afterSpec {
        mockServer.stop()
        resolver.close()
    }

    given("a TcpConnectExecutor") {

        `when`("the target port is open") {
            val result = executor.execute("127.0.0.1", mockServer.localPort, timeoutMs = 5000)

            then("it reports a successful connection with a latency reading") {
                result.isConnected.shouldBeTrue()
                result.latencyMs.shouldNotBeNull() shouldBeGreaterThanOrEqual 0
                result.error.shouldBeNull()
            }
        }

        `when`("the target port is closed") {
            // Spin up a dedicated mock server just to grab a free port, then stop it so nothing is listening there
            val throwaway = ClientAndServer.startClientAndServer(0)
            val closedPort = throwaway.localPort
            throwaway.stop()

            val result = executor.execute("127.0.0.1", closedPort, timeoutMs = 2000)

            then("it reports a failed connection with an error and no latency") {
                result.isConnected.shouldBeFalse()
                result.latencyMs.shouldBeNull()
                result.error.shouldNotBeNull()
            }
        }

        `when`("the host cannot be resolved") {
            val result = executor.execute("does-not-exist.invalid", 80, timeoutMs = 2000)

            then("it reports a failed connection with an error") {
                result.isConnected.shouldBeFalse()
                result.latencyMs.shouldBeNull()
                result.error.shouldNotBeNull()
            }
        }
    }

    given("a TcpConnectExecutor whose name resolution hangs") {

        val hangingResolver: (String) -> InetAddress = {
            Thread.sleep(RESOLVER_HANG_MS)
            InetAddress.getLoopbackAddress()
        }
        val hangingResolution = BoundedHostnameResolver(HostnameResolver(hangingResolver))
        val hangingExecutor = TcpConnectExecutor(hangingResolution, ProxyTunnel(hangingResolution))

        `when`("a check runs against it") {
            val timeoutMs = 200
            val startedAt = System.nanoTime()
            val result = hangingExecutor.execute("slow-dns.example", 80, timeoutMs = timeoutMs)
            val elapsedMs = ((System.nanoTime() - startedAt) / 1_000_000L).toInt()

            then("the check gives up around the timeout instead of blocking on the resolver") {
                result.isConnected.shouldBeFalse()
                result.latencyMs.shouldBeNull()
                result.error.shouldNotBeNull() shouldContain "timed out"
                // Must return close to the timeout, well before the resolver would have unblocked
                elapsedMs shouldBeLessThan RESOLVER_HANG_MS.toInt()
            }
        }

        afterSpec {
            hangingResolution.close()
        }
    }

    given("a TcpConnectExecutor whose name resolution is slow but succeeds") {

        val slowResolver: (String) -> InetAddress = {
            Thread.sleep(SLOW_RESOLVER_MS)
            InetAddress.getByName("127.0.0.1")
        }
        val slowResolution = BoundedHostnameResolver(HostnameResolver(slowResolver))
        val slowExecutor = TcpConnectExecutor(slowResolution, ProxyTunnel(slowResolution))

        `when`("a check connects to an open port through it") {
            val result = slowExecutor.execute("slow-but-ok.example", mockServer.localPort, timeoutMs = 5000)

            then("the reported latency reflects only the handshake, not the resolution time") {
                result.isConnected.shouldBeTrue()
                result.error.shouldBeNull()
                // The handshake on loopback is near-instant, so the latency must stay well below the
                // resolver delay - proving DNS time is excluded from the measurement.
                result.latencyMs.shouldNotBeNull() shouldBeLessThan SLOW_RESOLVER_MS.toInt()
            }
        }

        afterSpec {
            slowResolution.close()
        }
    }

    given("a TcpConnectExecutor with a blocking resolver and concurrent checks for the same host") {

        val invocationCount = AtomicInteger(0)
        val releaseResolver = CountDownLatch(1)
        val blockingResolver: (String) -> InetAddress = {
            invocationCount.incrementAndGet()
            releaseResolver.await()
            InetAddress.getLoopbackAddress()
        }
        val blockingResolution = BoundedHostnameResolver(HostnameResolver(blockingResolver))
        val dedupExecutor = TcpConnectExecutor(blockingResolution, ProxyTunnel(blockingResolution))

        `when`("several checks target the same unresolved host at once") {
            val threads = (1..CONCURRENT_CHECKS).map {
                Thread { dedupExecutor.execute("same-host.example", 80, timeoutMs = 200) }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join(THREAD_JOIN_TIMEOUT_MS) }

            then("only a single resolver lookup is started for that host") {
                invocationCount.get() shouldBe 1
            }
        }

        afterSpec {
            releaseResolver.countDown()
            blockingResolution.close()
        }
    }

    given("a TcpConnectExecutor that connects through a proxy") {

        // Only the proxy can resolve this name, so a check that connects to it proves the target is never resolved
        // locally
        val proxyOnlyHost = "localhost"
        val localResolution = BoundedHostnameResolver(
            HostnameResolver { host ->
                if (host == proxyOnlyHost) throw UnknownHostException(host) else InetAddress.getByName(host)
            }
        )
        val proxiedExecutor = TcpConnectExecutor(localResolution, ProxyTunnel(localResolution))
        val connectProxy = TestConnectProxy()
        val authenticatedProxy = TestConnectProxy(CREDENTIALS.username, CREDENTIALS.password)

        fun proxy(port: Int, credentials: ProxyCredentials? = null) = ConfiguredProxy(
            name = "corporate-egress",
            type = ProxyType.HTTP,
            host = "127.0.0.1",
            port = port,
            credentials = credentials,
        )

        `when`("the target port is open") {
            val result = proxiedExecutor.execute(proxyOnlyHost, mockServer.localPort, 5000, proxy(connectProxy.port))

            then("it reports a successful connection, established by the proxy that resolved the target") {
                result.isConnected.shouldBeTrue()
                result.latencyMs.shouldNotBeNull() shouldBeGreaterThanOrEqual 0
                result.error.shouldBeNull()
                connectProxy.tunnels shouldContainExactly listOf("$proxyOnlyHost:${mockServer.localPort}")
            }
        }

        `when`("the same target is checked without the proxy") {
            val result = proxiedExecutor.execute(proxyOnlyHost, mockServer.localPort, timeoutMs = 2000)

            then("it fails on the local resolution of the target") {
                result.isConnected.shouldBeFalse()
                result.error.shouldNotBeNull() shouldContain proxyOnlyHost
            }
        }

        `when`("the proxy can't connect to the target") {
            val throwaway = ClientAndServer.startClientAndServer(0)
            val closedPort = throwaway.localPort
            throwaway.stop()

            val result = proxiedExecutor.execute("127.0.0.1", closedPort, 5000, proxy(connectProxy.port))

            then("it reports a failed connection with the refusal of the proxy, naming the proxy") {
                result.isConnected.shouldBeFalse()
                result.latencyMs.shouldBeNull()
                result.error.shouldNotBeNull().let { error ->
                    error shouldStartWith "The check through the proxy \"corporate-egress\" failed"
                    error shouldContain "502"
                }
            }
        }

        `when`("the proxy requires credentials") {
            val authenticated = proxiedExecutor.execute(
                "127.0.0.1",
                mockServer.localPort,
                5000,
                proxy(authenticatedProxy.port, CREDENTIALS),
            )
            val wrongCredentials = proxiedExecutor.execute(
                "127.0.0.1",
                mockServer.localPort,
                5000,
                proxy(authenticatedProxy.port, ProxyCredentials(CREDENTIALS.username, "wrong")),
            )

            then("only the check with the right credentials connects") {
                authenticated.isConnected.shouldBeTrue()
                wrongCredentials.isConnected.shouldBeFalse()
                wrongCredentials.latencyMs.shouldBeNull()
                wrongCredentials.error.shouldNotBeNull() shouldContain "407"
            }
        }

        `when`("the proxy is unreachable") {
            val throwaway = ClientAndServer.startClientAndServer(0)
            val closedPort = throwaway.localPort
            throwaway.stop()

            val result = proxiedExecutor.execute("127.0.0.1", mockServer.localPort, 2000, proxy(closedPort))

            // The target itself is reachable, so a fallback to a direct connection would have succeeded
            then("it reports a failed connection naming the proxy, instead of connecting directly") {
                result.isConnected.shouldBeFalse()
                result.latencyMs.shouldBeNull()
                result.error.shouldNotBeNull() shouldStartWith
                    "The check through the proxy \"corporate-egress\" failed"
            }
        }

        afterSpec {
            connectProxy.close()
            authenticatedProxy.close()
            localResolution.close()
        }
    }
}) {
    companion object {
        private val CREDENTIALS = ProxyCredentials(username = "kuvasz", password = "s3cret")
        private const val RESOLVER_HANG_MS = 3000L
        private const val SLOW_RESOLVER_MS = 500L
        private const val CONCURRENT_CHECKS = 5
        private const val THREAD_JOIN_TIMEOUT_MS = 2000L
    }
}
