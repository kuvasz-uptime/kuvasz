package com.kuvaszuptime.kuvasz.services.docker.client

import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckRequestConfigurator
import com.kuvaszuptime.kuvasz.services.docker.DockerConnection
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.MediaType
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * A deliberately minimal HTTP/1.1 exchange for the read-only Docker Engine API endpoints the checker uses.
 *
 * The JDK's `HttpClient` cannot speak to a unix domain socket (JDK-8377806), so that transport needs hand-written
 * framing regardless. Writing it once and running both transports through it keeps their timeout and parsing
 * behavior identical, rather than having two HTTP stacks that can drift.
 */
internal object DockerHttpFraming {

    private const val CRLF = "\r\n"
    private const val LF = '\n'.code
    private const val MAX_BODY_BYTES = 1 shl 20
    private const val CHUNK_RADIX = 16
    private const val HTTP_VERSION = "HTTP/1.1"
    private const val CONNECTION_CLOSE = "close"
    private const val CHUNKED_ENCODING = "chunked"

    fun exchange(connection: DockerConnection, path: String, hostHeader: String): DockerHttpResponse {
        writeRequest(connection.output, path, hostHeader)

        val input = BufferedInputStream(connection.input)
        val statusCode = readStatusCode(input)
        val headers = readHeaders(input)
        return DockerHttpResponse(statusCode = statusCode, body = readBody(input, headers))
    }

    private fun StringBuilder.appendHeader(header: String, value: String) {
        append("$header: $value").append(CRLF)
    }

    private fun writeRequest(output: OutputStream, path: String, hostHeader: String) {
        // `Connection: close` keeps this stateless: no pooling, no keep-alive bookkeeping, and the daemon signals the
        // end of a length-less body by closing the stream.
        val head = buildString {
            append(listOf(HttpMethod.GET.name, path, HTTP_VERSION).joinToString(" ")).append(CRLF)
            appendHeader(HttpHeaders.HOST, hostHeader)
            appendHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
            appendHeader(HttpHeaders.USER_AGENT, HttpCheckRequestConfigurator.USER_AGENT)
            appendHeader(HttpHeaders.CONNECTION, CONNECTION_CLOSE)
            append(CRLF)
        }
        output.write(head.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun readStatusCode(input: InputStream): Int {
        val statusLine = readLine(input)
        return statusLine?.split(' ')?.getOrNull(1)?.toIntOrNull()
            ?: throw IOException(
                "Malformed HTTP status line from the daemon: [${statusLine ?: "<connection closed>"}]"
            )
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        // readLine only caps a single line, so the header section as a whole gets the same budget
        var headerBytes = 0L
        while (true) {
            val line = readLine(input)
            if (line.isNullOrEmpty()) return headers
            headerBytes += line.length
            ensureWithinLimit(headerBytes)
            val separator = line.indexOf(':')
            if (separator > 0) {
                // A repeated header is folded into one comma separated value, the way RFC 9110 lets a recipient
                // combine them
                val name = line.take(separator).trim().lowercase()
                headers.merge(name, line.substring(separator + 1).trim()) { first, next -> "$first, $next" }
            }
        }
    }

    private fun readBody(input: InputStream, headers: Map<String, String>): String {
        val contentLength = headers[HttpHeaders.CONTENT_LENGTH.lowercase()]
        val transferEncoding = headers[HttpHeaders.TRANSFER_ENCODING.lowercase()]
        val bytes = when {
            transferEncoding?.contains(CHUNKED_ENCODING, ignoreCase = true) == true -> readChunked(input)
            contentLength != null -> readExactly(input, parseContentLength(contentLength))
            // Neither is set, so the body runs until the daemon closes the connection
            else -> input.readNBytes(MAX_BODY_BYTES + 1).also { ensureWithinLimit(it.size.toLong()) }
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun parseContentLength(value: String): Int {
        val length = value.toLongOrNull()?.takeIf { it >= 0 }
            ?: throw IOException("Malformed Content-Length in the daemon's response: [$value]")
        ensureWithinLimit(length)
        return length.toInt()
    }

    private fun readChunked(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        var size = parseChunkSize(readLine(input))
        while (size > 0) {
            ensureWithinLimit(buffer.size().toLong() + size)
            buffer.write(readExactly(input, size))
            readLine(input) // the CRLF closing the chunk
            size = parseChunkSize(readLine(input))
        }
        return buffer.toByteArray()
    }

    private fun parseChunkSize(line: String?): Int =
        line?.substringBefore(';')?.trim()?.toIntOrNull(CHUNK_RADIX)?.takeIf { it >= 0 }
            ?: throw IOException("Malformed chunk size in the daemon's response: [${line ?: "<connection closed>"}]")

    private fun readExactly(input: InputStream, length: Int): ByteArray =
        input.readNBytes(length).also { bytes ->
            if (bytes.size < length) {
                throw IOException("The daemon closed the connection after ${bytes.size} of $length bytes")
            }
        }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            when (val byte = input.read()) {
                -1 -> return buffer.takeIf { it.size() > 0 }?.toString(StandardCharsets.US_ASCII)
                LF -> return buffer.toString(StandardCharsets.US_ASCII).removeSuffix("\r")
                else -> {
                    buffer.write(byte)
                    ensureWithinLimit(buffer.size().toLong())
                }
            }
        }
    }

    /**
     * Takes a Long, so a running total cannot overflow past the check.
     */
    private fun ensureWithinLimit(size: Long) {
        if (size > MAX_BODY_BYTES) {
            throw IOException("The daemon's response exceeds the $MAX_BODY_BYTES byte limit")
        }
    }
}
