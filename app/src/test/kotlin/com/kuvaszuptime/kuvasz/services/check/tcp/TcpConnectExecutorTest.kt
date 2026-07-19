package com.kuvaszuptime.kuvasz.services.check.tcp

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.mockserver.integration.ClientAndServer
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TcpConnectExecutorTest : BehaviorSpec({

    val executor = TcpConnectExecutor()

    lateinit var mockServer: ClientAndServer

    beforeSpec {
        mockServer = ClientAndServer.startClientAndServer(0)
    }

    afterSpec {
        mockServer.stop()
        executor.close()
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
        val resolverPool = Executors.newCachedThreadPool()
        val hangingExecutor = TcpConnectExecutor(resolver = hangingResolver, resolverExecutor = resolverPool)

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
            hangingExecutor.close()
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
        val resolverPool = Executors.newCachedThreadPool()
        val dedupExecutor = TcpConnectExecutor(resolver = blockingResolver, resolverExecutor = resolverPool)

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
            dedupExecutor.close()
        }
    }
}) {
    companion object {
        private const val RESOLVER_HANG_MS = 3000L
        private const val CONCURRENT_CHECKS = 5
        private const val THREAD_JOIN_TIMEOUT_MS = 2000L
    }
}
