package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class TcpMonitorRepositoryTest(
    private val tcpMonitorRepository: TcpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the fetchWithProxyNotIn() method") {

            `when`("there are direct monitors, and ones with a configured and a dangling proxy") {
                createTcpMonitor(tcpMonitorRepository, monitorName = "direct")
                createTcpMonitor(tcpMonitorRepository, monitorName = "proxied", proxy = "egress")
                createTcpMonitor(tcpMonitorRepository, monitorName = "dangling", proxy = "removed")

                then("it should only return the ones whose proxy is not among the given ones") {
                    val names = tcpMonitorRepository.fetchWithProxyNotIn(setOf("egress")).map { it.name }
                    names shouldContainExactlyInAnyOrder listOf("dangling")
                }

                then("it should return every proxied monitor, when no proxy is given") {
                    val names = tcpMonitorRepository.fetchWithProxyNotIn(emptySet()).map { it.name }
                    names shouldContainExactlyInAnyOrder listOf("proxied", "dangling")
                }
            }

            `when`("every proxy of the monitors is given") {
                createTcpMonitor(tcpMonitorRepository, proxy = "egress")

                then("it should return nothing") {
                    tcpMonitorRepository.fetchWithProxyNotIn(setOf("egress")).shouldBeEmpty()
                }
            }
        }
    }
}
