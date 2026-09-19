package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
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
    private val dockerMonitorRepository: DockerMonitorRepository,
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

            `when`("there is a Docker monitor only") {
                createDockerMonitor(dockerMonitorRepository)

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

        given("the findById() method") {

            `when`("it is called with the ID of a Docker monitor") {
                val monitor = createDockerMonitor(dockerMonitorRepository)

                then("it should return the record from the Docker table") {
                    val found: DockerMonitorRecord? =
                        sharedMonitorRepository.findById(NumericMonitorID(MonitorType.DOCKER, monitor.id))

                    found?.id shouldBe monitor.id
                    found?.name shouldBe monitor.name
                    found?.dockerHost shouldBe monitor.dockerHost
                    found?.container shouldBe monitor.container
                }
            }

            `when`("it is called with a Docker ID that does not exist") {
                then("it should return null") {
                    val found: DockerMonitorRecord? =
                        sharedMonitorRepository.findById(NumericMonitorID(MonitorType.DOCKER, -1L))

                    found shouldBe null
                }
            }
        }
    }
}
