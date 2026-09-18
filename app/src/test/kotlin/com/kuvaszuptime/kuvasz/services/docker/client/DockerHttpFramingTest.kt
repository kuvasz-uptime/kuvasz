package com.kuvaszuptime.kuvasz.services.docker.client

import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckRequestConfigurator
import com.kuvaszuptime.kuvasz.services.docker.DockerConnection
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

private const val LIMIT_BYTES = 1 shl 20

/** Runs one exchange against a canned response, returning the request that was sent along with the parsed response. */
private fun exchange(response: String): Pair<String, DockerHttpResponse> {
    val sent = ByteArrayOutputStream()
    val connection = DockerConnection(
        input = ByteArrayInputStream(response.toByteArray(StandardCharsets.UTF_8)),
        output = sent,
        resource = {},
    )
    val parsed = DockerHttpFraming.exchange(connection, "/containers/x/json", hostHeader = "localhost")
    return sent.toString(StandardCharsets.US_ASCII) to parsed
}

class DockerHttpFramingTest : BehaviorSpec({

    given("a response with a Content-Length body") {

        val (sentRequest, response) = exchange(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 15\r\n\r\n{\"State\":{\"a\"}}"
        )

        then("the status and body should be read") {
            response.statusCode shouldBe 200
            response.body shouldBe "{\"State\":{\"a\"}}"
        }

        then("the request should be a closing HTTP/1.1 GET") {
            sentRequest shouldContain "GET /containers/x/json HTTP/1.1\r\n"
            sentRequest shouldContain "Host: localhost\r\n"
            sentRequest shouldContain "User-Agent: ${HttpCheckRequestConfigurator.USER_AGENT}\r\n"
            sentRequest shouldContain "Connection: close\r\n"
            sentRequest.endsWith("\r\n\r\n") shouldBe true
        }
    }

    given("a chunked response") {

        val (_, response) = exchange(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" +
                "5\r\n{\"Sta\r\n" +
                "4;ext=1\r\nte\":\r\n" +
                "2\r\n{}\r\n" +
                "0\r\n\r\n"
        )

        then("every chunk should be joined, and chunk extensions ignored") {
            response.statusCode shouldBe 200
            response.body shouldBe "{\"State\":{}"
        }
    }

    given("a response that spreads Transfer-Encoding over two header lines") {

        val (_, response) = exchange(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: identity\r\nTransfer-Encoding: chunked\r\n\r\n2\r\n{}\r\n0\r\n\r\n"
        )

        then("the values should be combined, so the chunked encoding is still recognised") {
            response.body shouldBe "{}"
        }
    }

    given("a response with neither Content-Length nor chunked encoding") {

        val (_, response) = exchange("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"State\":null}")

        then("the body should be read until the daemon closes the connection") {
            response.body shouldBe "{\"State\":null}"
        }
    }

    given("an error response") {

        val (_, response) = exchange(
            "HTTP/1.1 404 Not Found\r\nContent-Length: 34\r\n\r\n{\"message\":\"No such container: x\"}"
        )

        then("the status code should be surfaced with the body") {
            response.statusCode shouldBe 404
            response.body shouldContain "No such container"
        }
    }

    given("a response over the $LIMIT_BYTES byte limit") {

        `when`("its Content-Length announces a larger body") {
            val exception = shouldThrow<IOException> {
                exchange("HTTP/1.1 200 OK\r\nContent-Length: ${LIMIT_BYTES + 1}\r\n\r\n")
            }

            then("it should be rejected before the body is read") {
                exception.message shouldContain "exceeds the $LIMIT_BYTES byte limit"
            }
        }

        `when`("a body without any length runs past it") {
            val exception = shouldThrow<IOException> {
                exchange("HTTP/1.1 200 OK\r\n\r\n" + "a".repeat(LIMIT_BYTES + 1))
            }

            then("it should be rejected instead of silently truncated") {
                exception.message shouldContain "exceeds the $LIMIT_BYTES byte limit"
            }
        }

        `when`("its header section runs past it, one short line at a time") {
            val exception = shouldThrow<IOException> {
                exchange("HTTP/1.1 200 OK\r\n" + "X-Pad: ${"a".repeat(1_000)}\r\n".repeat(1_100) + "\r\n")
            }

            then("it should be rejected, even though no single line is over the limit") {
                exception.message shouldContain "exceeds the $LIMIT_BYTES byte limit"
            }
        }

        `when`("a later chunk size would overflow the running total") {
            val exception = shouldThrow<IOException> {
                exchange("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n1\r\na\r\n7fffffff\r\n")
            }

            then("it should still be rejected") {
                exception.message shouldContain "exceeds the $LIMIT_BYTES byte limit"
            }
        }
    }

    given("a malformed response") {

        `when`("the status line is not HTTP") {
            val exception = shouldThrow<IOException> { exchange("nonsense\r\n\r\n") }

            then("it should be rejected") {
                exception.message shouldContain "Malformed HTTP status line"
            }
        }

        `when`("the daemon closes without answering") {
            val exception = shouldThrow<IOException> { exchange("") }

            then("it should be rejected") {
                exception.message shouldContain "<connection closed>"
            }
        }

        `when`("the Content-Length is negative") {
            val exception = shouldThrow<IOException> {
                exchange("HTTP/1.1 200 OK\r\nContent-Length: -1\r\n\r\n{}")
            }

            then("it should be rejected") {
                exception.message shouldContain "Malformed Content-Length"
            }
        }

        `when`("the body is shorter than its Content-Length") {
            val exception = shouldThrow<IOException> {
                exchange("HTTP/1.1 200 OK\r\nContent-Length: 15\r\n\r\n{\"S")
            }

            then("the truncation should be reported, rather than handing on a partial body") {
                exception.message shouldContain "closed the connection after 3 of 15 bytes"
            }
        }

        `when`("a chunk size is not hexadecimal") {
            val exception = shouldThrow<IOException> {
                exchange("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\nbody\r\n")
            }

            then("it should be rejected") {
                exception.message shouldContain "Malformed chunk size"
            }
        }

        `when`("a chunk size is negative") {
            val exception = shouldThrow<IOException> {
                exchange("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n-5\r\nbody\r\n")
            }

            then("it should be rejected rather than read as the last chunk") {
                exception.message shouldContain "Malformed chunk size"
            }
        }

        `when`("the chunked body is truncated") {
            val exception = shouldThrow<IOException> {
                exchange("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\n{\"Sta\r\n")
            }

            then("it should be rejected") {
                exception.message shouldContain "Malformed chunk size"
            }
        }
    }
})
