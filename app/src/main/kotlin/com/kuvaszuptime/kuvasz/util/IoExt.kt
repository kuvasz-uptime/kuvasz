package com.kuvaszuptime.kuvasz.util

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

private const val LF = '\n'.code

fun Closeable.closeQuietly() {
    runCatching { close() }
}

/**
 * Reads exactly [length] bytes.
 *
 * @throws IOException built by [onEndOfStream] from the number of bytes that did arrive, if the stream ends before.
 */
fun InputStream.readExactly(length: Int, onEndOfStream: (received: Int) -> IOException): ByteArray =
    readNBytes(length).also { bytes ->
        if (bytes.size < length) throw onEndOfStream(bytes.size)
    }

/**
 * Reads a line of an HTTP message head, byte by byte, so nothing that follows it is consumed. The line ends with an
 * LF, optionally preceded by a CR.
 *
 * @return the line without its ending, what is left of the stream if it ends before an LF, or null if it has already
 * ended.
 * @throws IOException built by [onTooLong], if the line is longer than [maxBytes].
 */
fun InputStream.readHttpLine(maxBytes: Int, onTooLong: () -> IOException): String? {
    val buffer = ByteArrayOutputStream()
    while (true) {
        when (val byte = read()) {
            -1 -> return buffer.takeIf { it.size() > 0 }?.toString(StandardCharsets.US_ASCII)
            LF -> return buffer.toString(StandardCharsets.US_ASCII).removeSuffix("\r")
            else -> {
                buffer.write(byte)
                if (buffer.size() > maxBytes) throw onTooLong()
            }
        }
    }
}

/**
 * The status code of an HTTP status line, e.g. `HTTP/1.1 200 OK`, or null if there is none.
 */
fun httpStatusCodeOf(statusLine: String): Int? = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
