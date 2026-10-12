package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.SlackBotNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackApiResponse
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackPostMessageRequest
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.SlackApiException
import com.kuvaszuptime.kuvasz.services.integrations.SlackApiMethod
import com.kuvaszuptime.kuvasz.services.integrations.SlackBotClient
import com.kuvaszuptime.kuvasz.services.integrations.SlackBotService
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class SlackBotEventHandlerTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    integrationRepository: IntegrationRepository,
    slackBotConfigs: List<SlackBotNotificationConfig>,
    databaseEventHandler: DatabaseEventHandler,
) : EventHandlerTest(databaseEventHandler) {
    private val mockClient = mockk<SlackBotClient>()

    private val globalConfig = slackBotConfigs.first { it.enabled && it.global }
    private val otherConfig = slackBotConfigs.first { it.name == "test_implicitly_enabled" }
    private val excludingConfig = slackBotConfigs.first { it.name == "excluding_http_down" }
    private val disabledConfig = slackBotConfigs.first { !it.enabled }

    init {
        val eventDispatcher = EventDispatcher()
        val slackBotServiceSpy = spyk(SlackBotService(mockClient))

        SlackBotEventHandler(eventDispatcher, slackBotServiceSpy, integrationRepository)

        given("the SlackBotEventHandler - uptime events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorUpEvent(monitor, HttpStatus.OK, 1000, null)

                eventDispatcher.testDispatch(event)

                then("it should not post anything") {
                    verify(inverse = true) { slackBotServiceSpy.postMessage(any(), any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(globalConfig.id, otherConfig.id, disabledConfig.id),
                    sensitiveUrl = true,
                )
                val event = HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                mockSuccessfulPost()

                eventDispatcher.testDispatch(event)

                then("it should post a red message to the channel of every enabled integration") {
                    val messages = mutableListOf<String>()

                    verify(exactly = 1) { slackBotServiceSpy.postMessage(globalConfig, capture(messages), RED) }
                    verify(exactly = 1) { slackBotServiceSpy.postMessage(otherConfig, capture(messages), RED) }
                    verify(inverse = true) { slackBotServiceSpy.postMessage(disabledConfig, any(), any()) }

                    messages.forAll { message ->
                        message shouldContain "🚨 *Your monitor \"${monitor.name}\" (MASKED URL) is DOWN"
                    }

                    // As a colored attachment, to the channel of the integration
                    val request = mutableListOf<SlackPostMessageRequest>()

                    verify(exactly = 2) { mockClient.postMessage(any(), capture(request)) }
                    request.map { it.channel } shouldContainExactlyInAnyOrder
                        listOf(globalConfig.channelId, otherConfig.channelId)
                    request.forAll { it.attachments.single().color shouldBe RED }
                }
            }

            `when`("it receives a MonitorUpEvent after a MonitorDownEvent") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulPost()
                eventDispatcher.testDispatch(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                eventDispatcher.testDispatch(HttpMonitorUpEvent(monitor, HttpStatus.OK, 1000, downRecord))

                then("it should post a red, and then a green message") {
                    val messages = mutableListOf<String>()

                    verify(exactly = 1) { slackBotServiceSpy.postMessage(globalConfig, capture(messages), RED) }
                    verify(exactly = 1) { slackBotServiceSpy.postMessage(globalConfig, capture(messages), GREEN) }
                    messages[0] shouldContain "is DOWN (500)"
                    messages[1] shouldContain "is UP (200)"
                }
            }

            `when`("it receives a MonitorDownEvent of a type that an integration excludes") {
                val monitor = createHttpMonitor(httpMonitorRepository, integrations = listOf(excludingConfig.id))
                mockSuccessfulPost()

                eventDispatcher.testDispatch(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )

                then("it should not post to the excluding integration") {
                    verify(exactly = 1) { slackBotServiceSpy.postMessage(globalConfig, any(), RED) }
                    verify(inverse = true) { slackBotServiceSpy.postMessage(excludingConfig, any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent of another monitor type that is not excluded") {
                val monitor = createPushMonitor(pushMonitorRepository, integrations = listOf(excludingConfig.id))
                mockSuccessfulPost()

                eventDispatcher.testDispatch(PushMonitorDownEvent(monitor, "Missed heartbeat", null))

                then("it should post to the excluding integration as well") {
                    verify(exactly = 1) { slackBotServiceSpy.postMessage(excludingConfig, any(), RED) }
                }
            }
        }

        given("the SlackBotEventHandler - SSL events") {
            `when`("it receives an SSLInvalidEvent") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulPost()

                eventDispatcher.testDispatch(SSLInvalidEvent(monitor, SSLValidationError("ssl error"), null))

                then("it should post a red message") {
                    val message = mutableListOf<String>()

                    verify(exactly = 1) { slackBotServiceSpy.postMessage(globalConfig, capture(message), RED) }
                    message.single() shouldContain "has an INVALID certificate"
                }
            }

            `when`("it receives an SSLWillExpireEvent, and then an SSLValidEvent") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulPost()
                eventDispatcher.testDispatch(SSLWillExpireEvent(monitor, generateCertificateInfo(), null))
                val willExpireRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                eventDispatcher.testDispatch(SSLValidEvent(monitor, generateCertificateInfo(), willExpireRecord))

                then("it should post a yellow, and then a green message") {
                    val messages = mutableListOf<String>()

                    verify(exactly = 1) { slackBotServiceSpy.postMessage(globalConfig, capture(messages), YELLOW) }
                    verify(exactly = 1) { slackBotServiceSpy.postMessage(globalConfig, capture(messages), GREEN) }
                    messages[0] shouldContain "will expire soon"
                    messages[1] shouldContain "has a VALID certificate"
                }
            }
        }

        given("the SlackBotEventHandler - maintenance window events") {
            `when`("it receives a MaintenanceWindowStartEvent and a MaintenanceWindowEndEvent") {
                val window = createMaintenanceWindow(
                    dslContext,
                    integrations = listOf(otherConfig.id, disabledConfig.id),
                )
                mockSuccessfulPost()

                eventDispatcher.dispatch(MaintenanceWindowStartEvent(window))
                eventDispatcher.dispatch(MaintenanceWindowEndEvent(window))

                then("it should post a yellow, and then a green message only to the assigned & enabled integrations") {
                    val messages = mutableListOf<String>()

                    verify(exactly = 1) { slackBotServiceSpy.postMessage(otherConfig, capture(messages), YELLOW) }
                    verify(exactly = 1) { slackBotServiceSpy.postMessage(otherConfig, capture(messages), GREEN) }
                    verify(inverse = true) { slackBotServiceSpy.postMessage(globalConfig, any(), any()) }
                    verify(inverse = true) { slackBotServiceSpy.postMessage(disabledConfig, any(), any()) }
                    messages[0] shouldContain "Maintenance \"${window.name}\" has started"
                    messages[1] shouldContain "Maintenance \"${window.name}\" has ended"
                }
            }
        }

        given("the SlackBotEventHandler - DNS drift and Docker restart events") {
            `when`("it receives a DnsRecordsChangedEvent") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                mockSuccessfulPost()

                eventDispatcher.testDispatch(
                    DnsRecordsChangedEvent(
                        monitor = monitor,
                        previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                        currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                    )
                )

                then("it should post a blue message") {
                    val message = mutableListOf<String>()

                    verify(exactly = 1) { slackBotServiceSpy.postMessage(globalConfig, capture(message), BLUE) }
                    message.single() shouldContain "DNS records changed for monitor \"${monitor.name}\""
                }
            }

            `when`("it receives a DockerContainerRestartedEvent") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                mockSuccessfulPost()

                eventDispatcher.testDispatch(
                    DockerContainerRestartedEvent(monitor = monitor, previousRestartCount = 2, currentRestartCount = 3)
                )

                then("it should post a yellow message") {
                    val message = mutableListOf<String>()

                    verify(exactly = 1) { slackBotServiceSpy.postMessage(globalConfig, capture(message), YELLOW) }
                    message.single() shouldContain "The container of monitor \"${monitor.name}\" has been restarted"
                }
            }
        }

        given("the SlackBotEventHandler - error handling logic") {
            `when`("Slack reports an error") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                every { mockClient.postMessage(any(), any()) } returns
                    Single.error(SlackApiException(SlackApiMethod.CHAT_POST_MESSAGE, "not_in_channel", null))

                then("it should not throw an exception") {
                    shouldNotThrowAny {
                        eventDispatcher.testDispatch(
                            HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                        )
                    }
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }

    private fun mockSuccessfulPost() {
        every { mockClient.postMessage(any(), any()) } returns
            Single.just(SlackApiResponse(ok = true, error = null, needed = null, ts = "1700000000.000100"))
    }

    companion object {
        private const val RED = "#d63939"
        private const val YELLOW = "#f59f00"
        private const val GREEN = "#2fb344"
        private const val BLUE = "#066fd1"
    }
}
