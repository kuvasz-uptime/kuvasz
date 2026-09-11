package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class InternalMonitorControllerTest(
    private val client: InternalMonitorCategoryClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
) : DatabaseBehaviorSpec({

    given("the monitor categories endpoint") {

        `when`("there is no monitor at all") {
            then("it returns an empty list") {
                client.getCategories().shouldBeEmpty()
            }
        }

        `when`("none of the monitors is categorized") {
            createHttpMonitor(httpMonitorRepository, monitorName = "http-monitor", category = null)
            createPushMonitor(pushMonitorRepository, monitorName = "push-monitor", category = null)

            then("it returns an empty list") {
                client.getCategories().shouldBeEmpty()
            }
        }

        `when`("the same category is used by monitors of different types") {
            createHttpMonitor(httpMonitorRepository, monitorName = "http-monitor", category = "Payments")
            createPushMonitor(pushMonitorRepository, monitorName = "push-monitor", category = "Payments")
            createIcmpMonitor(icmpMonitorRepository, monitorName = "icmp-monitor", category = "Payments")

            then("it returns the category only once") {
                client.getCategories() shouldBe listOf("Payments")
            }
        }

        `when`("the same category is used by more monitors of the same type") {
            createHttpMonitor(httpMonitorRepository, monitorName = "http-monitor-1", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "http-monitor-2", category = "Payments")

            then("it returns the category only once") {
                client.getCategories() shouldBe listOf("Payments")
            }
        }

        `when`("there are categories with different casings") {
            createHttpMonitor(httpMonitorRepository, monitorName = "http-monitor", category = "search")
            createTcpMonitor(tcpMonitorRepository, monitorName = "tcp-monitor", category = "Payments")
            createDnsMonitor(dnsMonitorRepository, monitorName = "dns-monitor", category = "alerting")
            createIcmpMonitor(icmpMonitorRepository, monitorName = "icmp-monitor", category = "Web")

            then("they are ordered case-insensitively") {
                client.getCategories() shouldBe listOf("alerting", "Payments", "search", "Web")
            }
        }

        `when`("a category belongs to a disabled monitor only") {
            createHttpMonitor(
                httpMonitorRepository,
                monitorName = "http-monitor",
                enabled = false,
                category = "Payments",
            )

            then("it is returned too, because it's still a category that has been set up") {
                client.getCategories() shouldBe listOf("Payments")
            }
        }

        `when`("the uncategorized monitors are mixed with the categorized ones") {
            createHttpMonitor(httpMonitorRepository, monitorName = "http-monitor-1", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "http-monitor-2", category = null)
            createPushMonitor(pushMonitorRepository, monitorName = "push-monitor", category = null)

            then("only the categorized ones contribute") {
                client.getCategories() shouldBe listOf("Payments")
            }
        }
    }
})
