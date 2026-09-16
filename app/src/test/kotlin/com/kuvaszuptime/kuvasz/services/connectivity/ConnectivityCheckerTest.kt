package com.kuvaszuptime.kuvasz.services.connectivity

import com.kuvaszuptime.kuvasz.config.ConnectivityCheckConfig
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityState
import com.kuvaszuptime.kuvasz.services.check.tcp.SystemHostnameResolver
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpCheckResult
import com.kuvaszuptime.kuvasz.services.DispatcherFactory
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpConnectExecutor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.mockserver.integration.ClientAndServer
import kotlin.system.measureTimeMillis

class ConnectivityCheckerTest : BehaviorSpec({

    val executor = TcpConnectExecutor(SystemHostnameResolver())
    // The very dispatcher the application injects into the checker
    val dispatcher = DispatcherFactory().provideDispatcher()

    lateinit var openTarget: ClientAndServer
    var closedPort = 0

    beforeSpec {
        openTarget = ClientAndServer.startClientAndServer(0)
        // Spin up a dedicated mock server just to grab a free port, then stop it so nothing is listening there
        val throwaway = ClientAndServer.startClientAndServer(0)
        closedPort = throwaway.localPort
        throwaway.stop()
    }

    afterSpec {
        openTarget.stop()
        executor.close()
    }

    fun config(targets: List<String>) = ConnectivityCheckConfig().apply {
        this.targets = targets
        this.timeoutSeconds = 2
    }

    fun monitor(ignoreConnectivityCheck: Boolean = false): HttpMonitorRecord =
        HttpMonitorRecord().setIgnoreConnectivityCheck(ignoreConnectivityCheck)

    fun failedResult(error: String = "Connection refused") =
        TcpCheckResult(isConnected = false, latencyMs = null, error = error)

    val successfulResult = TcpCheckResult(isConnected = true, latencyMs = 1, error = null)

    given("an enabled connectivity checker that hasn't run a probe yet") {

        val checker = ConnectivityChecker(config(listOf("127.0.0.1:$closedPort")), executor, dispatcher)

        `when`("its state is queried") {

            then("it is UNKNOWN, and it deliberately lets the checks run") {
                checker.getStatus().state shouldBe ConnectivityState.UNKNOWN
                checker.isOutboundConnectivityLost().shouldBeFalse()
                checker.isCheckSuppressedFor(monitor()).shouldBeFalse()
            }
        }
    }

    given("an enabled connectivity checker with only unreachable targets") {

        val checker = ConnectivityChecker(config(listOf("127.0.0.1:$closedPort")), executor, dispatcher)

        `when`("a check runs") {
            checker.check()
            val status = checker.getStatus()

            then("the connectivity is considered lost") {
                status.state shouldBe ConnectivityState.DOWN
                status.areChecksSuspended.shouldBeTrue()
                status.downSince.shouldNotBeNull()
                status.lastCheckedAt.shouldNotBeNull()
                status.lastSuccessfulCheckAt.shouldBeNull()
                status.lastError.shouldNotBeNull() shouldContain "127.0.0.1:$closedPort"
                checker.isOutboundConnectivityLost().shouldBeTrue()
            }

            then("the checks of the monitors are suppressed, unless they opted out of it") {
                checker.isCheckSuppressedFor(monitor()).shouldBeTrue()
                checker.isCheckSuppressedFor(monitor(ignoreConnectivityCheck = true)).shouldBeFalse()
            }
        }
    }

    given("an enabled connectivity checker whose first target is unreachable") {

        val checker = ConnectivityChecker(
            config(listOf("127.0.0.1:$closedPort", "127.0.0.1:${openTarget.localPort}")),
            executor,
            dispatcher,
        )

        `when`("a check runs") {
            checker.check()
            val status = checker.getStatus()

            then("a single reachable target is enough to consider the connectivity up") {
                status.state shouldBe ConnectivityState.UP
                status.areChecksSuspended.shouldBeFalse()
                status.lastSuccessfulCheckAt.shouldNotBeNull()
                status.downSince.shouldBeNull()
                status.lastError.shouldBeNull()
                checker.isCheckSuppressedFor(monitor()).shouldBeFalse()
            }
        }
    }

    given("a connectivity checker that stays down across multiple probes") {

        val mockExecutor = mockk<TcpConnectExecutor>()
        every { mockExecutor.execute(any(), any(), any()) } returns failedResult()
        val checker = ConnectivityChecker(config(listOf("1.1.1.1:53")), mockExecutor, dispatcher)

        `when`("a second probe fails too") {
            checker.check(confirmFailures = false)
            val firstStatus = checker.getStatus()
            checker.check(confirmFailures = false)
            val secondStatus = checker.getStatus()

            then("the start of the outage is kept instead of being pushed forward") {
                firstStatus.downSince.shouldNotBeNull()
                secondStatus.downSince shouldBe firstStatus.downSince
                secondStatus.state shouldBe ConnectivityState.DOWN
            }

            then("the last check timestamp still moves along with it") {
                secondStatus.lastCheckedAt.shouldNotBeNull() shouldBeAfter firstStatus.downSince!!.minusNanos(1)
            }
        }
    }

    given("a connectivity checker that has lost its connectivity") {

        val mockExecutor = mockk<TcpConnectExecutor>()
        val checker = ConnectivityChecker(config(listOf("1.1.1.1:53")), mockExecutor, dispatcher)

        `when`("the connectivity is restored") {
            every { mockExecutor.execute(any(), any(), any()) } returns failedResult()
            checker.check(confirmFailures = false)
            checker.getStatus().state shouldBe ConnectivityState.DOWN

            every { mockExecutor.execute(any(), any(), any()) } returns successfulResult
            checker.check()
            val status = checker.getStatus()

            then("the state flips back and the whole outage is closed at once") {
                status.state shouldBe ConnectivityState.UP
                status.downSince.shouldBeNull()
                status.lastError.shouldBeNull()
                status.lastSuccessfulCheckAt.shouldNotBeNull()
                status.areChecksSuspended.shouldBeFalse()
            }
        }
    }

    given("the failure confirmation") {

        `when`("a failing round is followed by a successful one") {

            val mockExecutor = mockk<TcpConnectExecutor>()
            every { mockExecutor.execute(any(), any(), any()) } returnsMany listOf(failedResult(), successfulResult)
            val checker = ConnectivityChecker(config(listOf("1.1.1.1:53")), mockExecutor, dispatcher)

            checker.check()

            then("a transient failure doesn't suspend anything") {
                checker.getStatus().state shouldBe ConnectivityState.UP
                checker.getStatus().lastError.shouldBeNull()
                verify(exactly = 2) { mockExecutor.execute(any(), any(), any()) }
            }
        }

        `when`("only the very last attempt succeeds") {

            val mockExecutor = mockk<TcpConnectExecutor>()
            every { mockExecutor.execute(any(), any(), any()) } returnsMany
                listOf(failedResult(), failedResult(), successfulResult)
            val checker = ConnectivityChecker(config(listOf("1.1.1.1:53")), mockExecutor, dispatcher)

            checker.check()

            then("every attempt is used up before giving up on the connectivity") {
                checker.getStatus().state shouldBe ConnectivityState.UP
                verify(exactly = ConnectivityChecker.FAILURE_CONFIRMATION_ATTEMPTS) {
                    mockExecutor.execute(any(), any(), any())
                }
            }
        }

        `when`("every round fails") {

            val mockExecutor = mockk<TcpConnectExecutor>()
            every { mockExecutor.execute(any(), any(), any()) } returns failedResult()
            val checker = ConnectivityChecker(config(listOf("1.1.1.1:53")), mockExecutor, dispatcher)

            val elapsedMs = measureTimeMillis { checker.check() }

            then("the failure is confirmed before the state flips") {
                checker.getStatus().state shouldBe ConnectivityState.DOWN
                verify(exactly = ConnectivityChecker.FAILURE_CONFIRMATION_ATTEMPTS) {
                    mockExecutor.execute(any(), any(), any())
                }
            }

            then("the re-tries are spaced out instead of being fired back to back") {
                val expectedDelays = ConnectivityChecker.FAILURE_CONFIRMATION_ATTEMPTS - 1

                elapsedMs shouldBeGreaterThanOrEqual expectedDelays * ConnectivityChecker.CONFIRMATION_DELAY_MILLIS
            }
        }

        `when`("it is explicitly skipped") {

            val mockExecutor = mockk<TcpConnectExecutor>()
            every { mockExecutor.execute(any(), any(), any()) } returns failedResult()
            val checker = ConnectivityChecker(config(listOf("1.1.1.1:53")), mockExecutor, dispatcher)

            val elapsedMs = measureTimeMillis { checker.check(confirmFailures = false) }

            then("the very first failure flips the state, without waiting for a re-try") {
                checker.getStatus().state shouldBe ConnectivityState.DOWN
                verify(exactly = 1) { mockExecutor.execute(any(), any(), any()) }
                elapsedMs shouldBeLessThan ConnectivityChecker.CONFIRMATION_DELAY_MILLIS
            }
        }

        `when`("the connectivity comes back during a re-try") {

            val mockExecutor = mockk<TcpConnectExecutor>()
            every { mockExecutor.execute(any(), any(), any()) } returns failedResult()
            val checker = ConnectivityChecker(config(listOf("1.1.1.1:53")), mockExecutor, dispatcher)
            checker.check(confirmFailures = false)
            checker.getStatus().state shouldBe ConnectivityState.DOWN

            every { mockExecutor.execute(any(), any(), any()) } returnsMany
                listOf(failedResult(), successfulResult)
            checker.check()

            then("the recovery is not held back by the confirmation logic") {
                checker.getStatus().state shouldBe ConnectivityState.UP
                checker.getStatus().downSince.shouldBeNull()
            }
        }
    }

    given("a connectivity checker whose executor blows up") {

        val mockExecutor = mockk<TcpConnectExecutor>()
        every { mockExecutor.execute(any(), any(), any()) } throws RuntimeException("Simulated failure")
        val checker = ConnectivityChecker(config(listOf("1.1.1.1:53")), mockExecutor, dispatcher)

        `when`("a check runs") {

            then("the error is not swallowed into a false DOWN state") {
                shouldThrow<RuntimeException> { checker.check() }

                checker.getStatus().state shouldBe ConnectivityState.UNKNOWN
                checker.isOutboundConnectivityLost().shouldBeFalse()
            }
        }
    }
})
