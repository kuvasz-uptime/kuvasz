package com.kuvaszuptime.kuvasz.services.check.tcp

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.mockserver.integration.ClientAndServer

class TcpConnectExecutorTest : BehaviorSpec({

    val executor = TcpConnectExecutor()

    lateinit var mockServer: ClientAndServer

    beforeSpec {
        mockServer = ClientAndServer.startClientAndServer(0)
    }

    afterSpec {
        mockServer.stop()
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
})
