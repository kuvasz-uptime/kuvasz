package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class HttpMonitorRepositoryTest(
    private val httpMonitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the fetchWithProxyNotIn() method") {

            `when`("there are direct monitors, and ones with a configured and a dangling proxy") {
                createHttpMonitor(httpMonitorRepository, monitorName = "direct")
                createHttpMonitor(httpMonitorRepository, monitorName = "proxied", proxy = "egress")
                createHttpMonitor(httpMonitorRepository, monitorName = "dangling", proxy = "removed")

                then("it should only return the ones whose proxy is not among the given ones") {
                    val names = httpMonitorRepository.fetchWithProxyNotIn(setOf("egress")).map { it.name }
                    names shouldContainExactlyInAnyOrder listOf("dangling")
                }

                then("it should return every proxied monitor, when no proxy is given") {
                    val names = httpMonitorRepository.fetchWithProxyNotIn(emptySet()).map { it.name }
                    names shouldContainExactlyInAnyOrder listOf("proxied", "dangling")
                }
            }

            `when`("every proxy of the monitors is given") {
                createHttpMonitor(httpMonitorRepository, proxy = "egress")

                then("it should return nothing") {
                    httpMonitorRepository.fetchWithProxyNotIn(setOf("egress")).shouldBeEmpty()
                }
            }
        }
    }
}
