package com.kuvaszuptime.kuvasz.services.proxy

import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import com.kuvaszuptime.kuvasz.services.network.BoundedHostnameResolver
import com.kuvaszuptime.kuvasz.services.network.HostnameResolver
import com.kuvaszuptime.kuvasz.services.network.SystemHostnameResolver
import com.kuvaszuptime.kuvasz.testutils.TestConnectProxy
import com.kuvaszuptime.kuvasz.util.elapsedMsSince
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CompletableFuture
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val TIMEOUT_MS = 2_000
private const val BANNER = "BANNER"
private const val TRICKLED_BYTES = 40
private const val TRICKLE_DELAY_MS = 50L
private const val RESOLVER_HANG_MS = 3_000L
private val CREDENTIALS = ProxyCredentials(username = "kuvasz", password = "s3cr3t")
private val TUNNEL = ProxyTunnel(BoundedHostnameResolver(SystemHostnameResolver()))

/**
 * A proxy that accepts a single connection, and plays [script] on it. Whatever [script] returns, or throws, ends up
 * in [result], so the test can assert on what the tunnel sent.
 */
private class FakeProxy<T>(private val script: (DataInputStream, OutputStream) -> T) : AutoCloseable {

    private val server = ServerSocket(0)
    val port: Int = server.localPort
    val result = CompletableFuture<T>()

    init {
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    script(DataInputStream(socket.getInputStream()), socket.getOutputStream())
                }
            }.fold(onSuccess = { result.complete(it) }, onFailure = { result.completeExceptionally(it) })
        }
    }

    fun received(): T = result.get(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)

    override fun close() = server.close()
}

private object TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private fun proxy(type: ProxyType, port: Int, credentials: ProxyCredentials? = null) = ConfiguredProxy(
    name = "test",
    type = type,
    host = "localhost",
    port = port,
    credentials = credentials,
)

private fun DataInputStream.readHead(): String {
    val head = StringBuilder()
    while (!head.endsWith("\r\n\r\n")) head.append(read().toChar())
    return head.toString()
}

private fun OutputStream.send(text: String) {
    write(text.toByteArray(Charsets.ISO_8859_1))
    flush()
}

private fun OutputStream.send(vararg bytes: Int) {
    write(bytes.map { it.toByte() }.toByteArray())
    flush()
}

// Keeps the connection open until the tunnel is done with it
private fun DataInputStream.awaitClose() {
    readAllBytes()
}

// A successful CONNECT response head of exactly [totalBytes], padded out by a header
private fun responseHeadOf(totalBytes: Int): String {
    val statusLine = "HTTP/1.1 200 OK\r\n"
    val headerName = "X-Filler: "
    val padding = totalBytes - statusLine.length - headerName.length - "\r\n\r\n".length
    return "$statusLine$headerName${"a".repeat(padding)}\r\n\r\n"
}

private fun Socket.readBanner(): String = use { String(it.getInputStream().readNBytes(BANNER.length)) }

private fun <T> openThrough(fake: FakeProxy<T>, type: ProxyType, host: String = "example.com", port: Int = 443) =
    fake.use { TUNNEL.open(proxy(type, fake.port), host, port, TIMEOUT_MS) }

class ProxyTunnelTest : ShouldSpec({

    lateinit var target: ClientAndServer
    lateinit var openProxy: ClientAndServer
    lateinit var authProxy: ClientAndServer

    beforeSpec {
        target = ClientAndServer.startClientAndServer(0)
        target.`when`(request().withPath("/hello.*")).respond(response().withStatusCode(200).withBody("hi"))
        // The targets of these tests all live on localhost
        openProxy = ClientAndServer.startClientAndServer(
            Configuration.configuration().forwardProxyBlockPrivateNetworks(false),
            0,
        )
        authProxy = ClientAndServer.startClientAndServer(
            Configuration.configuration()
                .forwardProxyBlockPrivateNetworks(false)
                .proxyAuthenticationUsername(CREDENTIALS.username)
                .proxyAuthenticationPassword(CREDENTIALS.password),
            0,
        )
    }

    afterSpec {
        target.stop()
        openProxy.stop()
        authProxy.stop()
    }

    context("an HTTP proxy") {

        should("send a CONNECT request and hand over the tunnel right after the response head") {
            FakeProxy { input, output ->
                val head = input.readHead()
                // The head comes in pieces, with a stray CR, and the target's first bytes right behind it
                output.send("HTTP/1.1 200 Connection established\r\nVia: fake\r")
                output.send("\r\n\r\n$BANNER")
                input.awaitClose()
                head
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.HTTP, fake.port), "example.com", 443, TIMEOUT_MS)
                    .readBanner() shouldBe BANNER

                val head = fake.received()
                head shouldBe "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n"
                head shouldNotContain "Proxy-Authorization"
            }
        }

        should("authenticate with the configured credentials") {
            FakeProxy { input, output ->
                val head = input.readHead()
                output.send("HTTP/1.0 200 OK\r\n\r\n$BANNER")
                input.awaitClose()
                head
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.HTTP, fake.port, CREDENTIALS), "example.com", 443, TIMEOUT_MS)
                    .readBanner() shouldBe BANNER

                val token = Base64.getEncoder().encodeToString("kuvasz:s3cr3t".toByteArray())
                fake.received() shouldContain "Proxy-Authorization: Basic $token\r\n"
            }
        }

        should("put the IPv6 address of the target between brackets") {
            FakeProxy { input, output ->
                val head = input.readHead()
                output.send("HTTP/1.1 200 OK\r\n\r\n")
                head
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.HTTP, fake.port), "::1", 8443, TIMEOUT_MS).close()

                fake.received() shouldContain "CONNECT [::1]:8443 HTTP/1.1\r\n"
            }
        }

        should("keep an already bracketed IPv6 address as is") {
            FakeProxy { input, output ->
                val head = input.readHead()
                output.send("HTTP/1.1 200 OK\r\n\r\n")
                head
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.HTTP, fake.port), "[::1]", 8443, TIMEOUT_MS).close()

                fake.received() shouldContain "CONNECT [::1]:8443 HTTP/1.1\r\n"
            }
        }

        should("report the status line when the proxy requires authentication") {
            val fake = FakeProxy { input, output ->
                input.readHead()
                output.send("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic\r\n\r\n")
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.HTTP) }

            exception.message shouldBe
                "The proxy refused to connect to example.com:443: HTTP/1.1 407 Proxy Authentication Required"
        }

        should("report the status line when the proxy can't reach the target") {
            val fake = FakeProxy { input, output ->
                input.readHead()
                output.send("HTTP/1.1 502 Bad Gateway\r\n\r\n")
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.HTTP) }

            exception.message shouldContain "HTTP/1.1 502 Bad Gateway"
        }

        should("reject a response that is not HTTP") {
            val fake = FakeProxy { input, output ->
                input.readHead()
                output.send("SSH-2.0-OpenSSH_9.6\r\n\r\n")
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.HTTP) }

            exception.message shouldBe "The proxy sent an invalid response to CONNECT: [SSH-2.0-OpenSSH_9.6]"
        }

        should("reject a response head that never ends") {
            val fake = FakeProxy { input, output ->
                input.readHead()
                output.send("HTTP/1.1 200 OK\r\n" + "X-Filler: ${"a".repeat(32 * 1024)}")
                input.awaitClose()
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.HTTP) }

            exception.message shouldBe "The response of the proxy to CONNECT is too large"
        }

        should("accept a response head that is exactly as large as allowed, line endings included") {
            val head = responseHeadOf(totalBytes = 16 * 1024)
            FakeProxy { input, output ->
                input.readHead()
                output.send(head + BANNER)
                input.awaitClose()
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.HTTP, fake.port), "example.com", 443, TIMEOUT_MS)
                    .readBanner() shouldBe BANNER
            }
        }

        should("reject a response head that is a single byte larger than allowed") {
            val fake = FakeProxy { input, output ->
                input.readHead()
                output.send(responseHeadOf(totalBytes = 16 * 1024 + 1))
                input.awaitClose()
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.HTTP) }

            exception.message shouldBe "The response of the proxy to CONNECT is too large"
        }

        should("fail if the proxy closes the connection in the middle of the response") {
            val fake = FakeProxy { input, output ->
                input.readHead()
                output.send("HTTP/1.1 200 OK\r\n")
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.HTTP) }

            exception.message shouldBe "The proxy closed the connection during the handshake"
        }

        should("time out if the proxy never answers") {
            val fake = FakeProxy { input, _ -> input.awaitClose() }
            val exception = shouldThrow<IOException> {
                fake.use { TUNNEL.open(proxy(ProxyType.HTTP, fake.port), "example.com", 443, 200) }
            }

            exception.shouldBeInstanceOf<SocketTimeoutException>()
        }

        should("time out at the end of the budget, if the proxy trickles its response") {
            val trickle: (DataInputStream, OutputStream) -> Unit = { input, output ->
                input.readHead()
                output.send("HTTP/1.1 200 OK\r\nX-Filler: ")
                // Every byte arrives well within a single read timeout, but the head never ends
                repeat(TRICKLED_BYTES) {
                    output.send("a")
                    Thread.sleep(TRICKLE_DELAY_MS)
                }
            }
            val fake = FakeProxy(trickle)
            val start = System.nanoTime()
            val exception = shouldThrow<IOException> {
                fake.use { TUNNEL.open(proxy(ProxyType.HTTP, fake.port), "example.com", 443, 300) }
            }

            exception.shouldBeInstanceOf<SocketTimeoutException>()
            elapsedMsSince(start) shouldBeLessThan TRICKLED_BYTES * TRICKLE_DELAY_MS.toInt() / 2
        }

        should("accept a response head with bare LF line endings") {
            FakeProxy { input, output ->
                input.readHead()
                output.send("HTTP/1.1 200 OK\nVia: fake\n\n$BANNER")
                input.awaitClose()
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.HTTP, fake.port), "example.com", 443, TIMEOUT_MS)
                    .readBanner() shouldBe BANNER
            }
        }

        should("reject a target host that would inject further lines into the CONNECT request") {
            val closedPort = ServerSocket(0).use { it.localPort }
            val exception = shouldThrow<IOException> {
                TUNNEL.open(
                    proxy(ProxyType.HTTP, closedPort),
                    "example.com:443 HTTP/1.1\r\nX-Injected: 1\r\n",
                    443,
                    TIMEOUT_MS,
                )
            }

            exception.message shouldBe "The target host contains whitespace or control characters"
        }
    }

    context("a SOCKS5 proxy") {

        should("connect to a domain name without authentication, and consume the bound address of the reply") {
            FakeProxy { input, output ->
                val greeting = input.readNBytes(3).toList()
                output.send(0x05, 0x00)
                val request = input.readNBytes(4 + 1 + "example.com".length + 2).toList()
                output.send(0x05, 0x00, 0x00, 0x01, 10, 0, 0, 1, 0x1F, 0x90)
                output.send(BANNER)
                input.awaitClose()
                greeting to request
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.SOCKS5, fake.port), "example.com", 443, TIMEOUT_MS)
                    .readBanner() shouldBe BANNER

                val (greeting, request) = fake.received()
                greeting shouldBe listOf<Byte>(0x05, 0x01, 0x00)
                request shouldBe listOf<Byte>(0x05, 0x01, 0x00, 0x03, "example.com".length.toByte()) +
                    "example.com".toByteArray().toList() +
                    listOf<Byte>(0x01, 0xBB.toByte())
            }
        }

        should("authenticate with the username and password, when the proxy asks for it") {
            FakeProxy { input, output ->
                val greeting = input.readNBytes(4).toList()
                output.send(0x05, 0x02)
                val auth = input.readNBytes(3 + "kuvasz".length + "s3cr3t".length).toList()
                output.send(0x01, 0x00)
                input.readNBytes(4 + 1 + "example.com".length + 2)
                // A domain name as the bound address this time
                output.send(0x05, 0x00, 0x00, 0x03, 5, 'p'.code, 'r'.code, 'o'.code, 'x'.code, 'y'.code, 0x1F, 0x90)
                output.send(BANNER)
                input.awaitClose()
                greeting to auth
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.SOCKS5, fake.port, CREDENTIALS), "example.com", 443, TIMEOUT_MS)
                    .readBanner() shouldBe BANNER

                val (greeting, auth) = fake.received()
                greeting shouldBe listOf<Byte>(0x05, 0x02, 0x00, 0x02)
                auth shouldBe listOf<Byte>(0x01, 6) + "kuvasz".toByteArray().toList() +
                    listOf<Byte>(6) + "s3cr3t".toByteArray().toList()
            }
        }

        should("skip authentication, when the proxy does not ask for it although there are credentials") {
            FakeProxy { input, output ->
                input.readNBytes(4)
                output.send(0x05, 0x00)
                input.readNBytes(4 + 1 + "example.com".length + 2)
                output.send(0x05, 0x00, 0x00, 0x01, 10, 0, 0, 1, 0x1F, 0x90)
                output.send(BANNER)
                input.awaitClose()
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.SOCKS5, fake.port, CREDENTIALS), "example.com", 443, TIMEOUT_MS)
                    .readBanner() shouldBe BANNER
            }
        }

        should("send an IPv4 literal as an address, without resolving anything") {
            FakeProxy { input, output ->
                input.readNBytes(3)
                output.send(0x05, 0x00)
                val request = input.readNBytes(4 + 4 + 2).toList()
                // An IPv6 bound address this time
                output.send(*intArrayOf(0x05, 0x00, 0x00, 0x04) + IntArray(16) + intArrayOf(0x1F, 0x90))
                request
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.SOCKS5, fake.port), "10.0.0.5", 22, TIMEOUT_MS).close()

                fake.received() shouldBe listOf<Byte>(0x05, 0x01, 0x00, 0x01, 10, 0, 0, 5, 0x00, 22)
            }
        }

        should("send a bracketed IPv6 literal as an address") {
            FakeProxy { input, output ->
                input.readNBytes(3)
                output.send(0x05, 0x00)
                val request = input.readNBytes(4 + 16 + 2).toList()
                output.send(0x05, 0x00, 0x00, 0x01, 10, 0, 0, 1, 0x1F, 0x90)
                request
            }.use { fake ->
                TUNNEL.open(proxy(ProxyType.SOCKS5, fake.port), "[::1]", 443, TIMEOUT_MS).close()

                val request = fake.received()
                request.take(4) shouldBe listOf<Byte>(0x05, 0x01, 0x00, 0x04)
                request.drop(4).take(16) shouldBe List<Byte>(15) { 0 } + listOf<Byte>(1)
            }
        }

        should("report the reason, when the proxy can't connect to the target") {
            val reasons = mapOf(
                0x01 to "general SOCKS server failure",
                0x02 to "connection not allowed by ruleset",
                0x03 to "network unreachable",
                0x04 to "host unreachable",
                0x05 to "connection refused",
                0x06 to "TTL expired",
                0x07 to "command not supported",
                0x08 to "address type not supported",
                0x42 to "unknown error",
            )
            reasons.forEach { (code, reason) ->
                val fake = FakeProxy { input, output ->
                    input.readNBytes(3)
                    output.send(0x05, 0x00)
                    input.readNBytes(4 + 1 + "example.com".length + 2)
                    output.send(0x05, code, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
                }
                val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.SOCKS5) }

                exception.message shouldBe
                    "The SOCKS5 proxy could not connect to example.com:443: $reason (0x%02X)".format(Locale.ROOT, code)
            }
        }

        should("reject the username and password, when the proxy does") {
            val fake = FakeProxy { input, output ->
                input.readNBytes(4)
                output.send(0x05, 0x02)
                input.readNBytes(3 + "kuvasz".length + "s3cr3t".length)
                output.send(0x01, 0x01)
            }
            val exception = shouldThrow<IOException> {
                fake.use {
                    TUNNEL.open(proxy(ProxyType.SOCKS5, fake.port, CREDENTIALS), "example.com", 443, TIMEOUT_MS)
                }
            }

            exception.message shouldBe "The SOCKS5 proxy rejected the username and password"
        }

        should("hint at the missing credentials, when the proxy accepts none of the offered methods") {
            val fake = FakeProxy { input, output ->
                input.readNBytes(3)
                output.send(0x05, 0xFF)
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.SOCKS5) }

            exception.message shouldBe "The SOCKS5 proxy accepted none of the offered authentication methods " +
                "(it probably requires a username and password)"
        }

        should("not hint at missing credentials, when there are some") {
            val fake = FakeProxy { input, output ->
                input.readNBytes(4)
                output.send(0x05, 0xFF)
            }
            val exception = shouldThrow<IOException> {
                fake.use {
                    TUNNEL.open(proxy(ProxyType.SOCKS5, fake.port, CREDENTIALS), "example.com", 443, TIMEOUT_MS)
                }
            }

            exception.message shouldBe "The SOCKS5 proxy accepted none of the offered authentication methods"
        }

        should("reject an authentication method that was not offered") {
            val fake = FakeProxy { input, output ->
                input.readNBytes(3)
                output.send(0x05, 0x02)
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.SOCKS5) }

            exception.message shouldBe "The SOCKS5 proxy chose an authentication method that was not offered"
        }

        should("reject a proxy that does not speak SOCKS5") {
            val fake = FakeProxy { input, output ->
                input.readNBytes(3)
                output.send(0x04, 0x00)
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.SOCKS5) }

            exception.message shouldBe "The proxy did not answer as a SOCKS5 proxy"
        }

        should("reject an unknown address type in the reply") {
            val fake = FakeProxy { input, output ->
                input.readNBytes(3)
                output.send(0x05, 0x00)
                input.readNBytes(4 + 1 + "example.com".length + 2)
                output.send(0x05, 0x00, 0x00, 0x07)
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.SOCKS5) }

            exception.message shouldBe "The SOCKS5 proxy sent an unknown address type"
        }

        should("fail if the proxy closes the connection in the middle of the handshake") {
            val fake = FakeProxy { input, output ->
                input.readNBytes(3)
                output.send(0x05)
            }
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.SOCKS5) }

            exception.message shouldBe "The proxy closed the connection during the handshake"
        }

        should("reject a host name that does not fit into a SOCKS5 request") {
            val fake = FakeProxy { input, output ->
                input.readNBytes(3)
                output.send(0x05, 0x00)
                input.awaitClose()
            }
            val longName = "a".repeat(250) + ".example.com"
            val exception = shouldThrow<IOException> { openThrough(fake, ProxyType.SOCKS5, host = longName) }

            exception.message shouldBe "The host name [$longName] is too long to be sent to a SOCKS5 proxy"
        }
    }

    context("an unreachable proxy") {

        should("fail to connect") {
            val closedPort = ServerSocket(0).use { it.localPort }

            shouldThrow<ConnectException> {
                TUNNEL.open(proxy(ProxyType.HTTP, closedPort), "example.com", 443, TIMEOUT_MS)
            }
        }

        should("give up at the end of the budget, if the name of the proxy can't be resolved in time") {
            val hangingResolver: (String) -> InetAddress = {
                Thread.sleep(RESOLVER_HANG_MS)
                InetAddress.getLoopbackAddress()
            }
            val resolver = BoundedHostnameResolver(HostnameResolver(hangingResolver))
            val start = System.nanoTime()
            val exception = resolver.use {
                shouldThrow<IOException> {
                    ProxyTunnel(resolver).open(proxy(ProxyType.HTTP, 1), "example.com", 443, 200)
                }
            }

            exception.message shouldBe "DNS resolution timed out after 200ms"
            elapsedMsSince(start) shouldBeLessThan RESOLVER_HANG_MS.toInt()
        }
    }

    context("a real proxy") {

        fun Socket.get(path: String): String = use { socket ->
            socket.getOutputStream().send(
                "GET $path HTTP/1.1\r\nHost: localhost:${target.port}\r\nConnection: close\r\n\r\n"
            )
            String(socket.getInputStream().readAllBytes())
        }

        should("carry a plaintext HTTP exchange through an anonymous HTTP proxy") {
            TestConnectProxy().use { connectProxy ->
                val response = TUNNEL
                    .open(proxy(ProxyType.HTTP, connectProxy.port), "localhost", target.port, TIMEOUT_MS)
                    .get("/hello-connect")

                response shouldContain "200"
                response shouldEndWith "hi"
                connectProxy.tunnels shouldBe listOf("localhost:${target.port}")
            }
        }

        should("carry a plaintext HTTP exchange through an authenticated HTTP proxy") {
            TestConnectProxy(CREDENTIALS.username, CREDENTIALS.password).use { connectProxy ->
                val response = TUNNEL
                    .open(proxy(ProxyType.HTTP, connectProxy.port, CREDENTIALS), "localhost", target.port, TIMEOUT_MS)
                    .get("/hello-connect-authenticated")

                response shouldEndWith "hi"
                connectProxy.tunnels shouldBe listOf("localhost:${target.port}")
            }
        }

        should("carry a TLS session through an authenticated HTTP proxy") {
            // MockServer terminates the TLS of a tunnel with its own certificate, which is fine to trust blindly here
            val trustAll = SSLContext.getInstance("TLS").apply { init(null, arrayOf(TrustAllManager), null) }
            val tunnel = TUNNEL
                .open(proxy(ProxyType.HTTP, authProxy.port, CREDENTIALS), "localhost", target.port, TIMEOUT_MS)
            val tls = trustAll.socketFactory.createSocket(tunnel, "localhost", target.port, true)

            tls.get("/hello-tls") shouldEndWith "hi"
            authProxy.retrieveRecordedRequests(request().withPath("/hello-tls")).toList() shouldHaveSize 1
        }

        should("be refused by an authenticated HTTP proxy with the wrong password") {
            val wrong = CREDENTIALS.copy(password = "wrong")
            val exception = shouldThrow<IOException> {
                TUNNEL.open(proxy(ProxyType.HTTP, authProxy.port, wrong), "localhost", target.port, TIMEOUT_MS)
            }

            exception.message shouldContain "407"
        }

        // MockServer does not enforce authentication on SOCKS5, so only the anonymous handshake is checked against it
        should("carry a plaintext HTTP exchange through an anonymous SOCKS5 proxy") {
            val response = TUNNEL
                .open(proxy(ProxyType.SOCKS5, openProxy.port), "localhost", target.port, TIMEOUT_MS)
                .get("/hello-socks5")

            response shouldContain "200"
            response shouldEndWith "hi"
            openProxy.retrieveRecordedRequests(request().withPath("/hello-socks5")).toList() shouldHaveSize 1
        }
    }
})
