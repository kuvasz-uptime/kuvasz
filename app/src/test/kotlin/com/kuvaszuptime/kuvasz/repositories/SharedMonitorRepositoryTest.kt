package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class SharedMonitorRepositoryTest(
    private val sharedMonitorRepository: SharedMonitorRepository,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the hasAnyMonitor() method") {

            `when`("there is no monitor of any type") {

                then("it should return false") {
                    sharedMonitorRepository.hasAnyMonitor() shouldBe false
                }
            }

            `when`("there is an HTTP monitor only") {
                createHttpMonitor(httpMonitorRepository)

                then("it should return true") {
                    sharedMonitorRepository.hasAnyMonitor() shouldBe true
                }
            }

            `when`("there is a push monitor only") {
                createPushMonitor(pushMonitorRepository)

                then("it should return true") {
                    sharedMonitorRepository.hasAnyMonitor() shouldBe true
                }
            }

            `when`("there is an ICMP monitor only") {
                createIcmpMonitor(icmpMonitorRepository)

                then("it should return true") {
                    sharedMonitorRepository.hasAnyMonitor() shouldBe true
                }
            }

            `when`("there is a TCP monitor only") {
                createTcpMonitor(tcpMonitorRepository)

                then("it should return true") {
                    sharedMonitorRepository.hasAnyMonitor() shouldBe true
                }
            }

            `when`("there is a DNS monitor only") {
                createDnsMonitor(dnsMonitorRepository)

                then("it should return true") {
                    sharedMonitorRepository.hasAnyMonitor() shouldBe true
                }
            }

            `when`("the only monitor of the setup is disabled") {
                createHttpMonitor(httpMonitorRepository, enabled = false)

                then("it should still return true, because a paused monitor is set up nevertheless") {
                    sharedMonitorRepository.hasAnyMonitor() shouldBe true
                }
            }
        }
    }
}
