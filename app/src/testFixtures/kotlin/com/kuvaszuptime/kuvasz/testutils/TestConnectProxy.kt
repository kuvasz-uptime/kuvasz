package com.kuvaszuptime.kuvasz.testutils

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A minimal HTTP proxy that relays CONNECT tunnels, optionally behind Basic authentication.
 *
 * MockServer can't stand in for it: after a CONNECT it always expects TLS, so it drops a tunnel that carries
 * plaintext HTTP.
 */
class TestConnectProxy(
    username: String? = null,
    password: String? = null,
    port: Int = 0,
) : AutoCloseable {

    private companion object {
        const val EMPTY_BODY = "Content-Length: 0\r\n"
    }

    private val server = ServerSocket(port)
    private val expectedToken = if (username != null && password != null) {
        Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    } else {
        null
    }

    val port: Int = server.localPort

    // The authority of every tunnel that was opened, e.g. `localhost:1080`, in the order of their opening
    val tunnels: MutableList<String> = CopyOnWriteArrayList()

    init {
        thread(isDaemon = true, name = "test-connect-proxy-$port") {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { client.use(::handle) }
            }
        }
    }

    private fun handle(client: Socket) {
        val output = client.getOutputStream()
        val head = client.getInputStream().readHead()
        val authority = head.substringBefore("\r\n").split(" ").getOrNull(1).orEmpty()
        val authorization = head.lines()
            .firstOrNull { it.startsWith("proxy-authorization:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()

        if (expectedToken != null && authorization != "Basic $expectedToken") {
            output.respond(
                "407 Proxy Authentication Required",
                "Proxy-Authenticate: Basic realm=\"test\"\r\n$EMPTY_BODY",
            )
            return
        }
        val upstream = runCatching {
            val host = authority.substringBeforeLast(':').removeSurrounding("[", "]")
            Socket(host, authority.substringAfterLast(':').toInt())
        }.getOrElse {
            output.respond("502 Bad Gateway", EMPTY_BODY)
            return
        }
        tunnels += authority
        upstream.use {
            output.respond("200 Connection established")
            val downstream = thread(isDaemon = true) {
                runCatching { upstream.getInputStream().transferTo(output) }
                runCatching { client.shutdownOutput() }
            }
            runCatching { client.getInputStream().transferTo(upstream.getOutputStream()) }
            runCatching { upstream.shutdownOutput() }
            downstream.join()
        }
    }

    private fun InputStream.readHead(): String {
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val next = read()
            if (next == -1) break
            head.append(next.toChar())
        }
        return head.toString()
    }

    private fun OutputStream.respond(status: String, headers: String = "") {
        write("HTTP/1.1 $status\r\n$headers\r\n".toByteArray())
        flush()
    }

    override fun close() = server.close()
}
