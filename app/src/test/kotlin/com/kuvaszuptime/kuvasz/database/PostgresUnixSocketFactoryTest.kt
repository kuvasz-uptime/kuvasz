package com.kuvaszuptime.kuvasz.database

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.Closeable
import java.io.IOException
import java.lang.management.ManagementFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

private const val READ_TIMEOUT_MS = 100
private const val LARGE_PAYLOAD_SIZE = 16 * 1024 * 1024
private const val BUFFER_SIZE = 64 * 1024

// More than any backlog the kernel may round a backlog of 1 up to
private const val CONNECT_ATTEMPTS = 10

private const val MISSING_SOCKET_PATH_MESSAGE =
    "The socketFactoryArg connection property has to be set to the path of PostgreSQL's socket file"

// The abstract operations of SocketImpl, including the ones inherited from SocketOptions
private const val UNSUPPORTED_OPERATIONS = 14

private fun echoServer() = TestUnixSocketServer { channel ->
    Channels.newInputStream(channel).transferTo(Channels.newOutputStream(channel))
}

private fun TestUnixSocketServer.connect(): Socket = PostgresUnixSocketFactory(path.toString()).createSocket().apply {
    connect(null)
}

class PostgresUnixSocketFactoryTest : BehaviorSpec({

    given("a socket connected to an echo server") {
        val server = autoClose(echoServer())

        `when`("bytes are written to it") {
            val socket = autoClose(server.connect())
            socket.getOutputStream().write("hello".toByteArray())
            socket.getOutputStream().write('!'.code)

            then("they should be read back, both in bulk and byte by byte") {
                val input = socket.getInputStream()
                val buffer = ByteArray(5)
                input.readNBytes(buffer, 0, 5) shouldBe 5
                String(buffer) shouldBe "hello"
                input.read() shouldBe '!'.code
            }
        }

        `when`("a byte above 127 is written to it") {
            val socket = autoClose(server.connect())
            socket.getOutputStream().write(0xFF)

            then("it should be read back as an unsigned value") {
                socket.getInputStream().read() shouldBe 0xFF
            }
        }

        `when`("zero bytes are requested") {
            val socket = autoClose(server.connect())

            then("the read should return immediately") {
                socket.getInputStream().read(ByteArray(1), 0, 0) shouldBe 0
            }
        }

        `when`("a read timeout is set and nothing arrives") {
            val socket = autoClose(server.connect())
            socket.soTimeout = READ_TIMEOUT_MS

            then("the read should time out, but the socket should stay usable") {
                socket.soTimeout shouldBe READ_TIMEOUT_MS
                val start = System.nanoTime()
                shouldThrow<SocketTimeoutException> { socket.getInputStream().read() }
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) shouldBeGreaterThanOrEqual
                    READ_TIMEOUT_MS.toLong()

                socket.getOutputStream().write('x'.code)
                socket.getInputStream().read() shouldBe 'x'.code
            }
        }

        `when`("a read with a timeout waits on an interrupted thread") {
            val socket = autoClose(server.connect())
            socket.soTimeout = READ_TIMEOUT_MS
            val threads = ManagementFactory.getThreadMXBean()

            then("it should ignore the interrupt like a regular socket, instead of spinning, and keep the flag set") {
                Thread.currentThread().interrupt()
                val cpuBefore = threads.currentThreadCpuTime
                shouldThrow<SocketTimeoutException> { socket.getInputStream().read() }
                val cpuUsedMs = TimeUnit.NANOSECONDS.toMillis(threads.currentThreadCpuTime - cpuBefore)

                // Also clears the flag, which would affect the rest of the spec otherwise
                Thread.interrupted() shouldBe true
                // Spinning would use the whole timeout
                cpuUsedMs shouldBeLessThan READ_TIMEOUT_MS / 2L
            }
        }

        `when`("it is closed by another thread while a read is waiting without a timeout") {
            val socket = autoClose(server.connect())
            val pendingRead = CompletableFuture<Result<Int>>()
            val reader = Thread { pendingRead.complete(runCatching { socket.getInputStream().read() }) }
            reader.start()
            // Closing it before the read starts waiting would test something else
            eventually(5.seconds) {
                reader.stackTrace.any { it.className.endsWith("SelectorImpl") } shouldBe true
            }
            socket.close()

            then("the read should fail instead of waiting forever") {
                shouldThrow<IOException> { pendingRead.get(5, TimeUnit.SECONDS).getOrThrow() }
                socket.isClosed shouldBe true
            }
        }

        `when`("it is connected again") {
            val socket = autoClose(server.connect())

            then("it should be refused, keeping the existing connection") {
                shouldThrow<SocketException> { socket.connect(null) }.message shouldContain "Already connected"
                socket.getOutputStream().write('x'.code)
                socket.getInputStream().read() shouldBe 'x'.code
            }
        }

        `when`("an operation that has no meaning for a unix domain socket is called") {
            val socket = autoClose(server.connect())

            then("it should fail, instead of silently acting on a TCP socket") {
                shouldThrow<SocketException> { socket.setSoLinger(true, 1) }.message shouldContain
                    "Not supported by a unix domain socket"
                shouldThrow<SocketException> { socket.trafficClass = 1 }
                socket.inetAddress shouldBe null
                socket.toString() shouldBe "UnixDomainSocket[path=${server.path}]"
            }
        }

        `when`("its remote address is requested") {
            val socket = autoClose(server.connect())

            then("it should be the socket file") {
                socket.remoteSocketAddress shouldBe UnixDomainSocketAddress.of(server.path)
            }
        }

        `when`("its input stream is closed") {
            val socket = autoClose(server.connect())
            val output = socket.getOutputStream()
            socket.getInputStream().close()

            then("the socket itself should be closed, just like a regular one") {
                socket.isClosed shouldBe true
                shouldThrow<IOException> { output.write('x'.code) }
                shouldThrow<SocketException> { socket.getOutputStream() }.message shouldContain "Socket is closed"
            }
        }

        `when`("its output stream is closed") {
            val socket = autoClose(server.connect())
            val input = socket.getInputStream()
            socket.getOutputStream().close()

            then("the socket itself should be closed, just like a regular one") {
                socket.isClosed shouldBe true
                shouldThrow<IOException> { input.read() }
                shouldThrow<SocketException> { socket.getInputStream() }.message shouldContain "Socket is closed"
            }
        }

        `when`("a negative read timeout is set") {
            val socket = autoClose(server.connect())

            then("it should be rejected") {
                shouldThrow<IllegalArgumentException> { socket.soTimeout = -1 }
                socket.soTimeout shouldBe 0
            }
        }

        `when`("its options are set") {
            val socket = autoClose(server.connect())

            then("the TCP only ones should be ignored, and the buffer sizes should be applied to the socket") {
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.tcpNoDelay shouldBe false
                socket.keepAlive shouldBe false
                socket.sendBufferSize = BUFFER_SIZE
                socket.receiveBufferSize = BUFFER_SIZE
                // The kernel may round them up, e.g. Linux doubles them
                socket.sendBufferSize shouldBeGreaterThanOrEqual BUFFER_SIZE
                socket.receiveBufferSize shouldBeGreaterThanOrEqual BUFFER_SIZE
            }
        }
    }

    given("a server that only starts reading after a while") {
        val received = CompletableFuture<Int>()
        val server = autoClose(
            TestUnixSocketServer { channel ->
                Thread.sleep(READ_TIMEOUT_MS.toLong())
                received.complete(Channels.newInputStream(channel).readAllBytes().size)
            }
        )

        `when`("a payload larger than the socket's buffer is written") {
            val socket = server.connect()
            socket.getOutputStream().write(ByteArray(LARGE_PAYLOAD_SIZE))
            socket.close()

            then("the write should wait for the server instead of dropping anything") {
                received.get(10, TimeUnit.SECONDS) shouldBe LARGE_PAYLOAD_SIZE
            }
        }
    }

    given("a server sending a payload larger than a single read transfers") {
        val payload = ByteArray(LARGE_PAYLOAD_SIZE) { it.toByte() }
        val server = autoClose(TestUnixSocketServer { channel -> Channels.newOutputStream(channel).write(payload) })

        `when`("the socket reads all of it at once") {
            val socket = autoClose(server.connect())

            then("it should arrive intact, split into several reads") {
                socket.getInputStream().readNBytes(LARGE_PAYLOAD_SIZE) shouldBe payload
            }
        }
    }

    given("a server that closes every connection immediately") {
        val server = autoClose(TestUnixSocketServer {})

        `when`("the socket reads from it") {
            val socket = autoClose(server.connect())

            then("it should reach the end of the stream") {
                socket.getInputStream().read() shouldBe -1
                socket.getInputStream().read(ByteArray(1), 0, 1) shouldBe -1
            }
        }
    }

    given("a socket that isn't connected yet") {
        val server = autoClose(echoServer())
        val socket = autoClose(PostgresUnixSocketFactory(server.path.toString()).createSocket())

        then("it should report that and refuse to hand out streams") {
            socket.isConnected shouldBe false
            shouldThrow<SocketException> { socket.getInputStream() }.message shouldContain "not connected"
            shouldThrow<SocketException> { socket.getOutputStream() }.message shouldContain "not connected"
            shouldThrow<SocketException> { socket.sendBufferSize }.message shouldContain "not connected"
            shouldThrow<SocketException> { socket.receiveBufferSize }.message shouldContain "not connected"
            socket.remoteSocketAddress shouldBe null
        }

        `when`("it is closed before connecting") {
            socket.close()

            then("connecting and the read timeout should be refused") {
                shouldThrow<SocketException> { socket.connect(null) }.message shouldContain "Socket is closed"
                shouldThrow<SocketException> { socket.soTimeout = 1 }.message shouldContain "Socket is closed"
                shouldThrow<SocketException> { socket.soTimeout }.message shouldContain "Socket is closed"
            }
        }
    }

    given("a server whose backlog is full, as it doesn't accept connections") {
        val directory = Files.createTempDirectory("kuvasz-pg")
        val path = directory.resolve(".s.PGSQL.5432")
        autoClose(
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path), 1) }
        )
        afterSpec {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }

        then("connecting should fail at once, instead of waiting for the backlog to free up") {
            val sockets = CopyOnWriteArrayList<Socket>()
            // A hanging connect would fail the test with a timeout
            val failure = CompletableFuture.supplyAsync {
                runCatching {
                    repeat(CONNECT_ATTEMPTS) { sockets.add(UnixDomainSocket(path).apply { connect(null) }) }
                }.exceptionOrNull()
            }.get(5, TimeUnit.SECONDS)

            failure.shouldBeInstanceOf<IOException>()
            sockets.forEach(Socket::close)
        }
    }

    given("a socket file that doesn't exist") {
        val directory = Files.createTempDirectory("kuvasz-pg")
        afterSpec { Files.deleteIfExists(directory) }
        val socket = autoClose(PostgresUnixSocketFactory(directory.resolve(".s.PGSQL.5432").toString()).createSocket())

        then("connecting should fail") {
            shouldThrow<IOException> { socket.connect(null) }
            socket.isConnected shouldBe false
        }
    }

    given("the factory's methods creating an already connected socket") {
        val server = autoClose(echoServer())
        val factory = PostgresUnixSocketFactory(server.path.toString())
        val localhost = InetAddress.getLoopbackAddress()

        then("they should all connect to the socket file, ignoring the host and port") {
            listOf(
                factory.createSocket("ignored", 5432),
                factory.createSocket("ignored", 5432, localhost, 0),
                factory.createSocket(localhost, 5432),
                factory.createSocket(localhost, 5432, localhost, 0),
            ).forEach { socket ->
                socket.use { connected ->
                    connected.isConnected shouldBe true
                    connected.getOutputStream().write('x'.code)
                    connected.getInputStream().read() shouldBe 'x'.code
                }
            }
        }
    }

    given("the path of the socket file missing from the connection properties") {
        val properties = Properties().apply {
            setProperty("socketFactory", PostgresUnixSocketFactory::class.java.name)
        }

        then("the factory should say so, both directly and through pgjdbc") {
            shouldThrow<IllegalArgumentException> { PostgresUnixSocketFactory(null) }.message shouldBe
                MISSING_SOCKET_PATH_MESSAGE

            val failure = shouldThrow<SQLException> {
                DriverManager.getConnection("jdbc:postgresql://localhost:5432/kuvasz", properties)
            }
            generateSequence<Throwable>(failure) { it.cause }.map { it.message }.toList() shouldContain
                MISSING_SOCKET_PATH_MESSAGE
        }
    }

    given("opening a selector failing while connecting") {

        `when`("the first one fails") {
            val serverSawClose = CompletableFuture<Int>()
            val server = autoClose(
                TestUnixSocketServer { channel -> serverSawClose.complete(Channels.newInputStream(channel).read()) }
            )
            val socket = autoClose(UnixDomainSocket(server.path) { throw IOException("Too many open files") })

            then("the channel opened already should be closed, and the socket should stay unconnected") {
                shouldThrow<IOException> { socket.connect(null) }.message shouldBe "Too many open files"
                serverSawClose.get(5, TimeUnit.SECONDS) shouldBe -1
                socket.isConnected shouldBe false
            }
        }

        `when`("it fails with an unchecked exception") {
            val serverSawClose = CompletableFuture<Int>()
            val server = autoClose(
                TestUnixSocketServer { channel -> serverSawClose.complete(Channels.newInputStream(channel).read()) }
            )
            val socket = autoClose(UnixDomainSocket(server.path) { throw IllegalStateException("Unexpected") })

            then("the channel opened already should be closed all the same") {
                shouldThrow<IllegalStateException> { socket.connect(null) }
                serverSawClose.get(5, TimeUnit.SECONDS) shouldBe -1
                socket.isConnected shouldBe false
            }
        }

        `when`("the second one fails") {
            val server = autoClose(echoServer())
            val opened = CopyOnWriteArrayList<Selector>()
            val socket = autoClose(
                UnixDomainSocket(server.path) {
                    if (opened.isNotEmpty()) throw IOException("Too many open files")
                    Selector.open().also(opened::add)
                }
            )

            then("the selector opened already should be closed too") {
                shouldThrow<IOException> { socket.connect(null) }
                opened.single().isOpen shouldBe false
            }
        }
    }

    given("a socket whose read selector gets closed underneath a read") {
        val server = autoClose(echoServer())
        val selectors = CopyOnWriteArrayList<Selector>()
        val socket = autoClose(UnixDomainSocket(server.path) { Selector.open().also(selectors::add) })
        socket.connect(null)
        selectors.first().close()

        then("the read should fail as the socket being closed") {
            shouldThrow<SocketException> { socket.getInputStream().read() }.message shouldContain "Socket is closed"
        }

        `when`("the thread reading is interrupted") {

            then("the interrupt flag should be kept even though the read fails") {
                Thread.currentThread().interrupt()
                shouldThrow<SocketException> { socket.getInputStream().read() }
                // Also clears the flag, which would affect the rest of the spec otherwise
                Thread.interrupted() shouldBe true
            }
        }
    }

    given("the implementation standing in for the TCP one of the socket") {
        val impl = UnsupportedSocketImpl()
        val operations = UnsupportedSocketImpl::class.java.declaredMethods
            .filterNot { it.isSynthetic || Modifier.isPrivate(it.modifiers) }

        then("every one of its operations should fail") {
            operations.size shouldBe UNSUPPORTED_OPERATIONS
            operations.forEach { operation ->
                operation.isAccessible = true
                val arguments = operation.parameterTypes.map<Class<*>, Any?> { type ->
                    when (type) {
                        Int::class.javaPrimitiveType -> 0
                        Boolean::class.javaPrimitiveType -> false
                        else -> null
                    }
                }
                shouldThrow<InvocationTargetException> { operation.invoke(impl, *arguments.toTypedArray()) }
                    .cause.shouldBeInstanceOf<SocketException>()
            }
        }
    }

    given("resources of which some fail to close") {
        val first = IOException("first")
        val second = IOException("second")
        val lastClosed = AtomicBoolean(false)
        val resources = listOf(
            Closeable { throw first },
            Closeable { throw second },
            Closeable { lastClosed.set(true) },
        )

        then("all of them should be closed, and the first failure should be rethrown with the rest suppressed") {
            val thrown = shouldThrow<IOException> { closeAll(resources) }
            thrown shouldBe first
            thrown.suppressed.toList() shouldBe listOf(second)
            lastClosed.get() shouldBe true
        }
    }
})
