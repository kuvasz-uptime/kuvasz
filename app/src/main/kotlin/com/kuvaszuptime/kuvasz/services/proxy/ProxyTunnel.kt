package com.kuvaszuptime.kuvasz.services.proxy

import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import com.kuvaszuptime.kuvasz.services.check.tcp.BoundedHostnameResolver
import com.kuvaszuptime.kuvasz.util.closeQuietly
import com.kuvaszuptime.kuvasz.util.elapsedMsSince
import com.kuvaszuptime.kuvasz.util.httpStatusCodeOf
import com.kuvaszuptime.kuvasz.util.readExactly
import com.kuvaszuptime.kuvasz.util.readHttpLine
import jakarta.inject.Singleton
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.Locale

/**
 * Opens raw TCP tunnels through a [ConfiguredProxy], for the checks that don't go through an HTTP client.
 *
 * Both handshakes are written by hand: the JDK has no HTTP CONNECT support for plain sockets, and its SOCKS
 * implementation can only authenticate through the JVM-wide `java.net.Authenticator`, which can't tell proxies apart.
 */
@Singleton
class ProxyTunnel(private val hostnameResolver: BoundedHostnameResolver) {

    private companion object {
        const val CRLF = "\r\n"
        const val MAX_RESPONSE_HEAD_BYTES = 16 * 1024
        const val MIN_TIMEOUT_MS = 1
        val IPV4_LITERAL = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        val SUCCESSFUL_STATUSES = 200..299

        const val SOCKS_VERSION: Byte = 0x05
        const val SOCKS_NO_AUTH: Byte = 0x00
        const val SOCKS_USERNAME_PASSWORD: Byte = 0x02
        const val SOCKS_NO_ACCEPTABLE_METHOD: Byte = 0xFF.toByte()
        const val SOCKS_AUTH_VERSION: Byte = 0x01
        const val SOCKS_SUCCEEDED: Byte = 0x00
        const val SOCKS_CMD_CONNECT: Byte = 0x01
        const val SOCKS_RESERVED: Byte = 0x00
        const val SOCKS_ATYP_IPV4: Byte = 0x01
        const val SOCKS_ATYP_DOMAIN: Byte = 0x03
        const val SOCKS_ATYP_IPV6: Byte = 0x04
        const val IPV4_LENGTH = 4
        const val IPV6_LENGTH = 16
        const val PORT_LENGTH = 2
        const val SOCKS_REPLY_HEAD_LENGTH = 4
        const val SOCKS_REPLY_INDEX = 1
        const val SOCKS_ADDRESS_TYPE_INDEX = 3
        const val MAX_SOCKS_HOST_LENGTH = 255
        const val BYTE_MASK = 0xFF
        const val BITS_PER_BYTE = 8

        val SOCKS_REPLIES = mapOf(
            0x01 to "general SOCKS server failure",
            0x02 to "connection not allowed by ruleset",
            0x03 to "network unreachable",
            0x04 to "host unreachable",
            0x05 to "connection refused",
            0x06 to "TTL expired",
            0x07 to "command not supported",
            0x08 to "address type not supported",
        )
    }

    /**
     * Connects to [proxy] and asks it to open a tunnel to [targetHost]:[targetPort]. The target's name is resolved by
     * the proxy, never locally.
     *
     * @param timeoutMs bounds resolving the proxy's name, connecting to it and the handshake, all together. It is left
     * on the returned socket as its read timeout.
     * @return a socket connected to the target through the proxy, ready to carry the target's protocol.
     * @throws IOException if the proxy can't be reached, it refuses to open the tunnel, or it does not open it in time.
     */
    fun open(proxy: ConfiguredProxy, targetHost: String, targetPort: Int, timeoutMs: Int): Socket {
        // It would end up in the request line of a CONNECT, where a line break could smuggle in further headers
        if (targetHost.any { it.isWhitespace() || it.isISOControl() }) {
            throw IOException("The target host contains whitespace or control characters")
        }
        val start = System.nanoTime()
        // Each step gets what the previous ones left over, but never 0, which would mean no timeout at all
        val remainingMs = { (timeoutMs - elapsedMsSince(start)).coerceAtLeast(MIN_TIMEOUT_MS) }
        val address = hostnameResolver.resolve(proxy.host, timeoutMs)
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(address, proxy.port), remainingMs())
            val input = BudgetedInputStream(socket, remainingMs)
            when (proxy.type) {
                ProxyType.HTTP -> httpConnect(socket, input, proxy.credentials, targetHost, targetPort)
                ProxyType.SOCKS5 -> socks5Connect(socket, input, proxy.credentials, targetHost, targetPort)
            }
            socket.soTimeout = timeoutMs
            return socket
        } catch (ex: IOException) {
            socket.closeQuietly()
            throw ex
        }
    }

    private fun httpConnect(
        socket: Socket,
        input: InputStream,
        credentials: ProxyCredentials?,
        targetHost: String,
        targetPort: Int,
    ) {
        val authority = "${targetHost.bracketedIfIpv6()}:$targetPort"
        val request = buildString {
            append("CONNECT $authority HTTP/1.1$CRLF")
            append("Host: $authority$CRLF")
            credentials?.let { (username, password) ->
                val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                append("Proxy-Authorization: Basic $token$CRLF")
            }
            append(CRLF)
        }
        socket.getOutputStream().apply {
            write(request.toByteArray(Charsets.ISO_8859_1))
            flush()
        }

        // The status line is checked before the headers are read, as something that is not an HTTP proxy might never
        // send the empty line that ends them
        val statusLine = input.readHeadLine(MAX_RESPONSE_HEAD_BYTES)
        val status = httpStatusCodeOf(statusLine)
            ?: throw IOException("The proxy sent an invalid response to CONNECT: [$statusLine]")
        if (status !in SUCCESSFUL_STATUSES) {
            throw IOException("The proxy refused to connect to $authority: $statusLine")
        }
        // The headers are of no use, but they have to be consumed, because whatever follows them already belongs to
        // the target, e.g. the greeting of a server that speaks first
        var budget = MAX_RESPONSE_HEAD_BYTES - statusLine.length
        do {
            val header = input.readHeadLine(budget)
            budget -= header.length
        } while (header.isNotEmpty())
    }

    private fun InputStream.readHeadLine(maxBytes: Int): String =
        readHttpLine(maxBytes) { IOException("The response of the proxy to CONNECT is too large") }
            ?: throw closedDuringHandshake()

    private fun socks5Connect(
        socket: Socket,
        input: InputStream,
        credentials: ProxyCredentials?,
        targetHost: String,
        targetPort: Int,
    ) {
        negotiateSocks5Method(socket, input, credentials)
        requestSocks5Tunnel(socket, input, targetHost, targetPort)
    }

    private fun negotiateSocks5Method(socket: Socket, input: InputStream, credentials: ProxyCredentials?) {
        val methods = if (credentials != null) {
            byteArrayOf(SOCKS_NO_AUTH, SOCKS_USERNAME_PASSWORD)
        } else {
            byteArrayOf(SOCKS_NO_AUTH)
        }
        socket.getOutputStream().apply {
            write(byteArrayOf(SOCKS_VERSION, methods.size.toByte()) + methods)
            flush()
        }

        val (version, method) = input.readHandshake(2)
        socks5MethodProblem(version, method, credentials)?.let { throw IOException(it) }
        if (method == SOCKS_USERNAME_PASSWORD && credentials != null) {
            socks5Authenticate(socket, input, credentials)
        }
    }

    private fun socks5MethodProblem(version: Byte, method: Byte, credentials: ProxyCredentials?): String? = when {
        version != SOCKS_VERSION -> "The proxy did not answer as a SOCKS5 proxy"
        method == SOCKS_NO_AUTH -> null
        method == SOCKS_USERNAME_PASSWORD && credentials != null -> null
        method == SOCKS_NO_ACCEPTABLE_METHOD ->
            "The SOCKS5 proxy accepted none of the offered authentication methods" +
                if (credentials == null) " (it probably requires a username and password)" else ""

        else -> "The SOCKS5 proxy chose an authentication method that was not offered"
    }

    private fun requestSocks5Tunnel(socket: Socket, input: InputStream, targetHost: String, targetPort: Int) {
        socket.getOutputStream().apply {
            write(byteArrayOf(SOCKS_VERSION, SOCKS_CMD_CONNECT, SOCKS_RESERVED) + targetHost.socksAddress())
            write(byteArrayOf((targetPort shr BITS_PER_BYTE).toByte(), targetPort.toByte()))
            flush()
        }

        val replyHead = input.readHandshake(SOCKS_REPLY_HEAD_LENGTH)
        val reply = replyHead[SOCKS_REPLY_INDEX]
        val addressType = replyHead[SOCKS_ADDRESS_TYPE_INDEX]
        if (reply != SOCKS_SUCCEEDED) {
            val code = reply.toInt() and BYTE_MASK
            val reason = SOCKS_REPLIES[code] ?: "unknown error"
            val hexCode = "0x%02X".format(Locale.ROOT, code)
            throw IOException("The SOCKS5 proxy could not connect to $targetHost:$targetPort: $reason ($hexCode)")
        }
        // The address the proxy bound for the tunnel is of no use here, but it has to be consumed, because whatever
        // follows it already belongs to the target
        input.readHandshake(input.boundAddressLength(addressType) + PORT_LENGTH)
    }

    private fun InputStream.boundAddressLength(addressType: Byte): Int = when (addressType) {
        SOCKS_ATYP_IPV4 -> IPV4_LENGTH
        SOCKS_ATYP_IPV6 -> IPV6_LENGTH
        SOCKS_ATYP_DOMAIN -> readHandshake(1)[0].toInt() and BYTE_MASK
        else -> throw IOException("The SOCKS5 proxy sent an unknown address type")
    }

    private fun socks5Authenticate(socket: Socket, input: InputStream, credentials: ProxyCredentials) {
        // Only ASCII is accepted for them, the same way the SOCKS5 handler of the HTTP checks sends them
        val username = credentials.username.toByteArray(Charsets.US_ASCII)
        val password = credentials.password.toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().apply {
            write(byteArrayOf(SOCKS_AUTH_VERSION, username.size.toByte()) + username)
            write(byteArrayOf(password.size.toByte()) + password)
            flush()
        }
        val (_, status) = input.readHandshake(2)
        if (status != SOCKS_SUCCEEDED) throw IOException("The SOCKS5 proxy rejected the username and password")
    }

    /**
     * The target as a SOCKS5 address. An IP literal is sent as such, and anything else as a domain name, so the proxy
     * resolves it.
     */
    private fun String.socksAddress(): ByteArray {
        val host = removePrefix("[").removeSuffix("]")
        if (host.isIpLiteral()) {
            // A literal is parsed, never looked up
            val address = InetAddress.getByName(host)
            val type = if (address is Inet4Address) SOCKS_ATYP_IPV4 else SOCKS_ATYP_IPV6
            return byteArrayOf(type) + address.address
        }
        val name = host.toByteArray(Charsets.US_ASCII)
        if (name.size > MAX_SOCKS_HOST_LENGTH) {
            throw IOException("The host name [$host] is too long to be sent to a SOCKS5 proxy")
        }
        return byteArrayOf(SOCKS_ATYP_DOMAIN, name.size.toByte()) + name
    }

    private fun String.isIpLiteral(): Boolean = ':' in this || matches(IPV4_LITERAL)

    private fun String.bracketedIfIpv6(): String = if (':' in this && !startsWith("[")) "[$this]" else this

    private fun InputStream.readHandshake(length: Int): ByteArray = readExactly(length) { closedDuringHandshake() }

    private fun closedDuringHandshake() = IOException("The proxy closed the connection during the handshake")

    /**
     * Reads the handshake from [socket] within what [remainingMs] has left of the budget, however many reads it takes:
     * the read timeout of the socket only bounds a single read, so a proxy that trickles its answer could otherwise
     * hold the tunnel open for much longer.
     */
    private class BudgetedInputStream(private val socket: Socket, private val remainingMs: () -> Int) : InputStream() {

        private val input = socket.getInputStream()

        override fun read(): Int {
            socket.soTimeout = remainingMs()
            return input.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            socket.soTimeout = remainingMs()
            return input.read(buffer, offset, length)
        }
    }
}
