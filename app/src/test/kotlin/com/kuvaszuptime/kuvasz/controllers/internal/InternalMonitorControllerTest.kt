package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest
class InternalMonitorControllerTest(
    private val client: InternalMonitorClient,
    @Client("/") private val httpClient: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the internal monitors endpoint") {

            `when`("there is no monitor at all") {
                then("it returns an empty list") {
                    client.getMonitors().shouldBeEmpty()
                }
            }

            `when`("there are monitors of every type, disabled ones too") {
                createHttpMonitor(httpMonitorRepository, monitorName = "delta")
                createPushMonitor(pushMonitorRepository, monitorName = "Bravo")
                createIcmpMonitor(icmpMonitorRepository, monitorName = "echo", enabled = false)
                createTcpMonitor(tcpMonitorRepository, monitorName = "Alpha")
                createDnsMonitor(dnsMonitorRepository, monitorName = "charlie")
                createDockerMonitor(dockerMonitorRepository, monitorName = "Foxtrot")

                then("it returns the type and the name of each, ordered by the name without regard to the case") {
                    client.getMonitors() shouldBe listOf(
                        MonitorID(MonitorType.TCP, "Alpha"),
                        MonitorID(MonitorType.PUSH, "Bravo"),
                        MonitorID(MonitorType.DNS, "charlie"),
                        MonitorID(MonitorType.HTTP_SSL, "delta"),
                        MonitorID(MonitorType.ICMP, "echo"),
                        MonitorID(MonitorType.DOCKER, "Foxtrot"),
                    )
                }

                then("they are serialized the same way as the monitors of a status page or a maintenance window") {
                    httpClient.retrieve("/api/internal/monitors").awaitFirst() shouldBe
                        """["tcp:Alpha","push:Bravo","dns:charlie","http:delta","icmp:echo","docker:Foxtrot"]"""
                }
            }

            `when`("monitors of different types have the same name") {
                createHttpMonitor(httpMonitorRepository, monitorName = "api")
                createTcpMonitor(tcpMonitorRepository, monitorName = "api")

                then("both of them are returned") {
                    client.getMonitors() shouldBe listOf(
                        MonitorID(MonitorType.HTTP_SSL, "api"),
                        MonitorID(MonitorType.TCP, "api"),
                    )
                }
            }
        }
    }
}
