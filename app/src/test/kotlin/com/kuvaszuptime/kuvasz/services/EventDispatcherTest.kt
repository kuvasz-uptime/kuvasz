@file:Suppress("TooGenericExceptionThrown")

package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.seconds

@MicronautTest(startApplication = false, transactional = false)
class EventDispatcherTest(
    private val monitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
) : DatabaseBehaviorSpec() {

    private val dispatcher = EventDispatcher()
    private var errorCnt = 0
    private var successfulInvocationCnt = 0

    private val receivedUptimeEvents = mutableListOf<UptimeMonitorEvent>()
    private val receivedSSLEvents = mutableListOf<SSLMonitorEvent>()
    private val receivedMaintenanceEvents = mutableListOf<MaintenanceWindowEvent>()
    private val receivedDriftEvents = mutableListOf<DnsRecordsChangedEvent>()

    init {
        afterContainer {
            receivedUptimeEvents.clear()
            receivedSSLEvents.clear()
            receivedMaintenanceEvents.clear()
            receivedDriftEvents.clear()
        }

        dispatcher.subscribeToHttpMonitorUpEvents { event ->
            if (event.latency == 123) {
                errorCnt++
                throw RuntimeException("Simulated error")
            } else {
                successfulInvocationCnt++
            }
        }
        // The subscriptions have to be set up here, upfront: they are established asynchronously, on an IO thread,
        // so subscribing from within a test case could easily miss the events dispatched right after it
        dispatcher.subscribeToUptimeMonitorEvents { event -> receivedUptimeEvents.add(event) }
        dispatcher.subscribeToSSLMonitorEvents { event -> receivedSSLEvents.add(event) }
        dispatcher.subscribeToMaintenanceWindowEvents { event -> receivedMaintenanceEvents.add(event) }
        dispatcher.subscribeToDnsRecordsChangedEvents { event -> receivedDriftEvents.add(event) }

        given("an event dispatcher") {

            `when`("an error occurs in a consumer") {
                val monitor = createHttpMonitor(monitorRepository)
                val monitorUpEvent = HttpMonitorUpEvent(
                    monitor,
                    HttpStatus.OK,
                    latency = 123,
                    previousEvent = null,
                )

                dispatcher.dispatch(monitorUpEvent)
                dispatcher.dispatch(monitorUpEvent.copy(latency = 343))

                then("it should not cancel the subscription") {
                    eventually(2.seconds) {
                        errorCnt shouldBe 1
                        successfulInvocationCnt shouldBe 1
                    }
                }
            }
        }

        given("the subscription to every uptime monitor event") {

            `when`("uptime events of all the different types are dispatched") {
                val httpMonitor = createHttpMonitor(monitorRepository)
                val pushMonitor = createPushMonitor(pushMonitorRepository)
                val icmpMonitor = createIcmpMonitor(icmpMonitorRepository)
                val tcpMonitor = createTcpMonitor(tcpMonitorRepository)
                val dnsMonitor = createDnsMonitor(dnsMonitorRepository)

                val events = listOf(
                    HttpMonitorUpEvent(httpMonitor, HttpStatus.OK, latency = 100, previousEvent = null),
                    HttpMonitorDownEvent(
                        httpMonitor,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        error = Exception("http error"),
                        previousEvent = null,
                    ),
                    PushMonitorUpEvent(pushMonitor, previousEvent = null),
                    PushMonitorDownEvent(pushMonitor, error = "push error", previousEvent = null),
                    IcmpMonitorUpEvent(icmpMonitor, previousEvent = null, latencyInMs = 5, packetLossPercentage = 0),
                    IcmpMonitorDownEvent(
                        icmpMonitor,
                        error = "icmp error",
                        previousEvent = null,
                        packetLossPercentage = 100,
                    ),
                    TcpMonitorUpEvent(tcpMonitor, previousEvent = null, latencyInMs = 5),
                    TcpMonitorDownEvent(tcpMonitor, error = "tcp error", previousEvent = null),
                    DnsMonitorUpEvent(dnsMonitor, previousEvent = null, latencyInMs = 5),
                    DnsMonitorDownEvent(dnsMonitor, error = "dns error", previousEvent = null),
                )
                events.forEach { dispatcher.dispatch(it) }

                then("it should receive every one of them") {
                    eventually(2.seconds) {
                        receivedUptimeEvents shouldContainExactlyInAnyOrder events
                    }
                }
            }
        }

        given("the subscription to every SSL monitor event") {

            `when`("SSL events of all the different types are dispatched") {
                val monitor = createHttpMonitor(monitorRepository)
                val certInfo = CertificateInfo(validTo = OffsetDateTime.now().plusDays(30))

                val events = listOf(
                    SSLValidEvent(monitor, certInfo, previousEvent = null),
                    SSLInvalidEvent(monitor, SSLValidationError("ssl error"), previousEvent = null),
                    SSLWillExpireEvent(monitor, certInfo, previousEvent = null),
                )
                events.forEach { dispatcher.dispatch(it) }

                then("it should receive every one of them") {
                    eventually(2.seconds) {
                        receivedSSLEvents shouldContainExactlyInAnyOrder events
                    }
                }
            }
        }

        given("the subscription to every maintenance window event") {

            `when`("maintenance events of all the different types are dispatched") {
                val window = createMaintenanceWindow(dslContext)

                val events = listOf(
                    MaintenanceWindowStartEvent(window),
                    MaintenanceWindowEndEvent(window),
                )
                events.forEach { dispatcher.dispatch(it) }

                then("it should receive every one of them") {
                    eventually(2.seconds) {
                        receivedMaintenanceEvents shouldContainExactlyInAnyOrder events
                    }
                }
            }
        }

        given("the subscription to DNS records changed events") {

            `when`("a DnsRecordsChangedEvent is dispatched") {
                val dnsMonitor = createDnsMonitor(dnsMonitorRepository)
                val event = DnsRecordsChangedEvent(
                    monitor = dnsMonitor,
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )

                dispatcher.dispatch(event)

                then("it should be received by the drift subscriber") {
                    eventually(2.seconds) {
                        receivedDriftEvents shouldContainExactlyInAnyOrder listOf(event)
                    }
                }
            }
        }
    }
}
