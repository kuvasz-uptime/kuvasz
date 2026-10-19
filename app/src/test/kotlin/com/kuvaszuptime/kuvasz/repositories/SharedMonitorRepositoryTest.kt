package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class SharedMonitorRepositoryTest(
    private val sharedMonitorRepository: SharedMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
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

        given("the countEnabled() method") {

            `when`("there are enabled and paused monitors of every type") {
                listOf(true, false).forEach { enabled ->
                    createHttpMonitor(httpMonitorRepository, enabled = enabled)
                    createPushMonitor(pushMonitorRepository, enabled = enabled)
                    createIcmpMonitor(icmpMonitorRepository, enabled = enabled)
                    createTcpMonitor(tcpMonitorRepository, enabled = enabled)
                    createDnsMonitor(dnsMonitorRepository, enabled = enabled)
                    createDockerMonitor(dockerMonitorRepository, enabled = enabled)
                }
                createHttpMonitor(httpMonitorRepository)

                then("it should count only the enabled ones, of every type together") {
                    sharedMonitorRepository.countEnabled() shouldBe 7
                }
            }

            `when`("there are no monitors at all") {

                then("it should return zero") {
                    sharedMonitorRepository.countEnabled() shouldBe 0
                }
            }
        }

        given("the fetchMonitorIds() method") {

            `when`("there are monitors of every type") {
                createHttpMonitor(httpMonitorRepository, monitorName = "delta")
                createPushMonitor(pushMonitorRepository, monitorName = "Bravo")
                createIcmpMonitor(icmpMonitorRepository, monitorName = "echo")
                createTcpMonitor(tcpMonitorRepository, monitorName = "Alpha")
                createDnsMonitor(dnsMonitorRepository, monitorName = "charlie")
                createDockerMonitor(dockerMonitorRepository, monitorName = "Foxtrot")

                then("it should return all of them, ordered by the name without regard to the case") {
                    sharedMonitorRepository.fetchMonitorIds() shouldBe listOf(
                        MonitorID(MonitorType.TCP, "Alpha"),
                        MonitorID(MonitorType.PUSH, "Bravo"),
                        MonitorID(MonitorType.DNS, "charlie"),
                        MonitorID(MonitorType.HTTP_SSL, "delta"),
                        MonitorID(MonitorType.ICMP, "echo"),
                        MonitorID(MonitorType.DOCKER, "Foxtrot"),
                    )
                }
            }

            `when`("there are no monitors at all") {

                then("it should return an empty list") {
                    sharedMonitorRepository.fetchMonitorIds().shouldBeEmpty()
                }
            }
        }

        given("the fetchNumericIds() method") {

            `when`("it is called with monitors of different types, some of which don't exist") {
                val http = createHttpMonitor(httpMonitorRepository, monitorName = "api")
                val tcp = createTcpMonitor(tcpMonitorRepository, monitorName = "api")
                val docker = createDockerMonitor(dockerMonitorRepository, monitorName = "app")
                createPushMonitor(pushMonitorRepository, monitorName = "api")
                createIcmpMonitor(icmpMonitorRepository, monitorName = "not asked for")

                then("it should return the IDs of only the existing ones, matched by both their type and name") {
                    sharedMonitorRepository.fetchNumericIds(
                        listOf(
                            MonitorID(MonitorType.HTTP_SSL, "api"),
                            MonitorID(MonitorType.TCP, "api"),
                            MonitorID(MonitorType.DOCKER, "app"),
                            MonitorID(MonitorType.DOCKER, "deleted"),
                            MonitorID(MonitorType.DNS, "api"),
                        )
                    ) shouldBe mapOf(
                        MonitorID(MonitorType.HTTP_SSL, "api") to http.id,
                        MonitorID(MonitorType.TCP, "api") to tcp.id,
                        MonitorID(MonitorType.DOCKER, "app") to docker.id,
                    )
                }
            }

            `when`("it is called without any monitors") {
                createHttpMonitor(httpMonitorRepository)

                then("it should return an empty map") {
                    sharedMonitorRepository.fetchNumericIds(emptyList()).shouldBeEmpty()
                }
            }
        }
    }
}
