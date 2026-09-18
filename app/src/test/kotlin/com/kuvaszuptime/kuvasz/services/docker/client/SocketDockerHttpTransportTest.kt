package com.kuvaszuptime.kuvasz.services.docker.client

import com.kuvaszuptime.kuvasz.config.DockerHostConfig
import com.kuvaszuptime.kuvasz.services.docker.DockerConnectionFactory
import com.kuvaszuptime.kuvasz.services.docker.DockerDaemonAddress
import com.kuvaszuptime.kuvasz.services.docker.DockerHost
import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import com.kuvaszuptime.kuvasz.services.docker.FakeDockerDaemon
import com.kuvaszuptime.kuvasz.services.docker.TestPki
import com.kuvaszuptime.kuvasz.services.docker.ssl.DockerSslContextProvider
import com.sun.management.UnixOperatingSystemMXBean
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.lang.management.ManagementFactory
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

private const val OK_RESPONSE = "HTTP/1.1 200 OK\r\nContent-Length: 12\r\n\r\n{\"State\":{}}"
private const val TIMEOUT_MS = 500
private const val GENEROUS_TIMEOUT_MS = 5_000
private const val INSPECT_PATH = "/containers/my-app/json"
private val HANG_UP_WAIT = 2.seconds
private const val FD_WARMUP = 5
private const val FAILED_HANDSHAKES = 40
private const val FD_SLACK = 10L
private val FD_SETTLE = 5.seconds

/** Process-wide open descriptor count, available on any Unix HotSpot; null elsewhere. */
private fun openFileDescriptorCount(): Long? =
    (ManagementFactory.getOperatingSystemMXBean() as? UnixOperatingSystemMXBean)?.openFileDescriptorCount

private fun tlsConfig(ca: String? = null, cert: String? = null, key: String? = null): DockerHostConfig.TlsConfig {
    val config = mockk<DockerHostConfig.TlsConfig>()
    every { config.ca } returns ca
    every { config.cert } returns cert
    every { config.key } returns key
    return config
}

private fun hostConfig(name: String, url: String, tls: DockerHostConfig.TlsConfig? = null): DockerHostConfig {
    val config = mockk<DockerHostConfig>()
    every { config.name } returns name
    every { config.url } returns url
    every { config.tls } returns tls
    return config
}

private class Fixture(vararg configs: DockerHostConfig) {
    private val registry = DockerHostRegistry(configs.toList())
    val transport = SocketDockerHttpTransport(DockerConnectionFactory(DockerSslContextProvider(registry)))
    fun host(name: String): DockerHost = registry[name] ?: error("Docker host [$name] was not resolved")
}

class SocketDockerHttpTransportTest : BehaviorSpec({

    val pki = TestPki.generate(Files.createTempDirectory("kuvasz-transport-pki"))

    given("a daemon on a unix domain socket") {

        val daemon = autoClose(FakeDockerDaemon.UnixSocket(OK_RESPONSE))
        val fixture = Fixture(hostConfig("local", "unix://${daemon.path}"))
        val transport = autoClose(fixture.transport)

        `when`("a container is inspected") {
            val response = transport.get(fixture.host("local"), INSPECT_PATH, GENEROUS_TIMEOUT_MS)

            then("the daemon should answer over the socket") {
                response.statusCode shouldBe 200
                response.body shouldBe "{\"State\":{}}"
            }

            then("the request should name the container and a localhost Host header") {
                daemon.receivedRequests shouldHaveSize 1
                daemon.receivedRequests.first() shouldContain "GET /containers/my-app/json HTTP/1.1"
                daemon.receivedRequests.first() shouldContain "Host: localhost"
            }
        }
    }

    given("a daemon on plaintext TCP") {

        val daemon = autoClose(FakeDockerDaemon.Tcp(OK_RESPONSE))
        val fixture = Fixture(hostConfig("plain", "tcp://localhost:${daemon.port}"))
        val transport = autoClose(fixture.transport)

        `when`("a container is inspected") {
            val response = transport.get(fixture.host("plain"), INSPECT_PATH, GENEROUS_TIMEOUT_MS)

            then("the daemon should answer") {
                response.statusCode shouldBe 200
            }

            then("the Host header should carry the real authority") {
                daemon.receivedRequests.first() shouldContain "Host: localhost:${daemon.port}"
            }
        }
    }

    given("a TLS daemon whose certificate is configured as the CA") {

        val daemon = autoClose(FakeDockerDaemon.Tcp(OK_RESPONSE, sslContext = pki.serverSslContext))
        val fixture = Fixture(
            hostConfig("tls", "tcp://localhost:${daemon.port}", tlsConfig(ca = pki.caPem.toString()))
        )
        val transport = autoClose(fixture.transport)

        `when`("a container is inspected") {
            val response = transport.get(fixture.host("tls"), INSPECT_PATH, GENEROUS_TIMEOUT_MS)

            then("the handshake should succeed without a client certificate") {
                response.statusCode shouldBe 200
                daemon.receivedRequests shouldHaveSize 1
            }
        }
    }

    given("a TLS daemon that demands a client certificate") {

        val daemon = autoClose(
            FakeDockerDaemon.Tcp(OK_RESPONSE, sslContext = pki.serverSslContext, needClientAuth = true)
        )
        val fixture = Fixture(
            hostConfig(
                "mtls",
                "tcp://localhost:${daemon.port}",
                tlsConfig(
                    ca = pki.caPem.toString(),
                    cert = pki.clientCertPem.toString(),
                    key = pki.clientKeyPem.toString(),
                ),
            ),
            hostConfig("no-cert", "tcp://localhost:${daemon.port}", tlsConfig(ca = pki.caPem.toString())),
        )
        val transport = autoClose(fixture.transport)

        `when`("the configured client certificate is presented") {
            val response = transport.get(fixture.host("mtls"), INSPECT_PATH, GENEROUS_TIMEOUT_MS)

            then("the mutual handshake should succeed and the request should arrive") {
                response.statusCode shouldBe 200
                daemon.receivedRequests.first() shouldContain "GET /containers/my-app/json"
            }
        }

        `when`("no client certificate is configured") {
            then("the handshake should fail") {
                shouldThrow<IOException> {
                    transport.get(fixture.host("no-cert"), INSPECT_PATH, GENEROUS_TIMEOUT_MS)
                }
            }
        }
    }

    given("a TLS daemon whose certificate is not trusted") {

        val daemon = autoClose(FakeDockerDaemon.Tcp(OK_RESPONSE, sslContext = pki.serverSslContext))
        // No `tls` block at all, so the JVM's default trust store is used, which does not know this certificate
        val fixture = Fixture(hostConfig("untrusted", "https://localhost:${daemon.port}"))
        val transport = autoClose(fixture.transport)

        `when`("a container is inspected") {
            val exception = shouldThrow<IOException> {
                transport.get(fixture.host("untrusted"), INSPECT_PATH, GENEROUS_TIMEOUT_MS)
            }

            then("the certificate should be rejected instead of silently trusted") {
                exception.message.orEmpty() shouldContain "PKIX"
            }
        }
    }

    given("a daemon that never answers") {

        `when`("it is reached over a unix socket") {
            val daemon = autoClose(FakeDockerDaemon.UnixSocket(holdOpen = true))
            val fixture = Fixture(hostConfig("slow-socket", "unix://${daemon.path}"))
            val transport = autoClose(fixture.transport)

            val exception = shouldThrow<IOException> {
                transport.get(fixture.host("slow-socket"), INSPECT_PATH, TIMEOUT_MS)
            }

            then("the exchange should be timed out by the caller, since a socket read has no SO_TIMEOUT") {
                exception.message shouldContain "timed out after ${TIMEOUT_MS}ms"
            }

            then("the connection should be hung up rather than left to a blocked worker") {
                eventually(HANG_UP_WAIT) { daemon.hangUps.get() shouldBe 1 }
            }
        }

        `when`("it is reached over TCP") {
            val daemon = autoClose(FakeDockerDaemon.Tcp(holdOpen = true))
            val fixture = Fixture(hostConfig("slow-tcp", "tcp://localhost:${daemon.port}"))
            val transport = autoClose(fixture.transport)

            val exception = shouldThrow<IOException> {
                transport.get(fixture.host("slow-tcp"), INSPECT_PATH, TIMEOUT_MS)
            }

            then("the exchange should time out, whether the deadline or the socket's own read timeout fires first") {
                exception.message shouldContain "timed out after ${TIMEOUT_MS}ms"
            }

            then("the connection should be hung up rather than left to a blocked worker") {
                eventually(HANG_UP_WAIT) { daemon.hangUps.get() shouldBe 1 }
            }
        }

        `when`("the caller is interrupted while waiting for it") {
            val daemon = autoClose(FakeDockerDaemon.UnixSocket(holdOpen = true))
            val fixture = Fixture(hostConfig("interrupted", "unix://${daemon.path}"))
            val transport = autoClose(fixture.transport)

            // The call runs on its own thread, so interrupting it cannot leak an interrupt into the spec's thread
            val failure = CompletableFuture<Throwable?>()
            val caller = thread(isDaemon = true) {
                failure.complete(
                    runCatching {
                        transport.get(fixture.host("interrupted"), INSPECT_PATH, GENEROUS_TIMEOUT_MS)
                    }.exceptionOrNull()
                )
            }
            // Only once the request is on the wire, so it is the wait that gets interrupted, not the submission
            eventually(HANG_UP_WAIT) { daemon.receivedRequests shouldHaveSize 1 }
            caller.interrupt()

            then("the wait should be abandoned") {
                failure.get(GENEROUS_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                    .shouldBeInstanceOf<InterruptedException>()
            }

            then("the worker should be stopped too, hanging up on the daemon") {
                eventually(HANG_UP_WAIT) { daemon.hangUps.get() shouldBe 1 }
            }
        }
    }

    given("a socket whose own timeout beats the request deadline") {

        val connectionFactory = mockk<DockerConnectionFactory>()
        every { connectionFactory.open(any(), any()) } throws SocketTimeoutException("Read timed out")
        val transport = autoClose(SocketDockerHttpTransport(connectionFactory))
        val host = DockerHost(
            name = "racing",
            url = "tcp://localhost:2375",
            address = DockerDaemonAddress.Tcp(host = "localhost", port = 2375, secure = false, tls = null),
        )

        `when`("a container is inspected") {
            val exception = shouldThrow<IOException> { transport.get(host, INSPECT_PATH, TIMEOUT_MS) }

            then("it should be reported as the same timeout, instead of the socket's own wording") {
                exception.message shouldBe "the request timed out after ${TIMEOUT_MS}ms"
                exception.cause?.cause.shouldBeInstanceOf<SocketTimeoutException>()
            }
        }
    }

    given("nothing listening on the configured port") {

        val closedPort = ServerSocket(0).use { it.localPort }
        val fixture = Fixture(hostConfig("gone", "tcp://localhost:$closedPort"))
        val transport = autoClose(fixture.transport)

        `when`("a container is inspected") {
            val exception = shouldThrow<IOException> {
                transport.get(fixture.host("gone"), INSPECT_PATH, TIMEOUT_MS)
            }

            then("the connection failure should be surfaced") {
                exception.message.orEmpty() shouldContain "onnection refused"
            }
        }
    }

    given("a TLS daemon that keeps rejecting the client") {

        val daemon = autoClose(
            FakeDockerDaemon.Tcp(OK_RESPONSE, sslContext = pki.serverSslContext, needClientAuth = true)
        )
        // Trusts the daemon, but presents no client certificate of its own, so every handshake fails
        val fixture = Fixture(
            hostConfig("rejected", "tcp://localhost:${daemon.port}", tlsConfig(ca = pki.caPem.toString()))
        )
        val transport = autoClose(fixture.transport)
        val host = fixture.host("rejected")

        `when`("the handshake fails over and over") {

            // Warm up first, so class loading and JIT do not read as growth
            repeat(FD_WARMUP) { runCatching { transport.get(host, INSPECT_PATH, GENEROUS_TIMEOUT_MS) } }
            val before = openFileDescriptorCount()
            repeat(FAILED_HANDSHAKES) { runCatching { transport.get(host, INSPECT_PATH, GENEROUS_TIMEOUT_MS) } }

            then("no socket should be stranded, because nothing owns one until the connection is built") {
                if (before != null) {
                    eventually(FD_SETTLE) {
                        val grown = (openFileDescriptorCount() ?: before) - before
                        grown shouldBeLessThan FD_SLACK
                    }
                }
            }
        }
    }
})
