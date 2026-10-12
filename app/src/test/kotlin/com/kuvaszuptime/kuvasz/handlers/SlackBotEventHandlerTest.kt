package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.jooq.enums.IntegrationThreadSubject
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.IntegrationThreadRecord
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IncidentNoteCreatedEvent
import com.kuvaszuptime.kuvasz.models.events.IncidentNoteUpdatedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.SlackTextFormatter
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.SlackBotNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackApiResponse
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackPostMessageRequest
import com.kuvaszuptime.kuvasz.models.handlers.slack.SlackReactionRequest
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.repositories.IntegrationThreadRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.SlackApiException
import com.kuvaszuptime.kuvasz.services.integrations.SlackApiMethod
import com.kuvaszuptime.kuvasz.services.integrations.SlackBotClient
import com.kuvaszuptime.kuvasz.services.integrations.SlackBotService
import com.kuvaszuptime.kuvasz.services.integrations.SlackColor
import com.kuvaszuptime.kuvasz.services.integrations.SlackError
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.SingleSubject

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class SlackBotEventHandlerTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    private val threadRepository: IntegrationThreadRepository,
    private val databaseEventHandler: DatabaseEventHandler,
    incidentRepository: IncidentRepository,
    integrationRepository: IntegrationRepository,
    slackBotConfigs: List<SlackBotNotificationConfig>,
) : EventHandlerTest(databaseEventHandler) {
    private val mockClient = mockk<SlackBotClient>()

    // Global, with reactions
    private val globalConfig = slackBotConfigs.first { it.enabled && it.global }

    // Not global, without reactions
    private val otherConfig = slackBotConfigs.first { it.name == "test_implicitly_enabled" }
    private val excludingConfig = slackBotConfigs.first { it.name == "excluding_http_down" }
    private val disabledConfig = slackBotConfigs.first { !it.enabled }

    private val formatter = SlackTextFormatter

    init {
        val eventDispatcher = EventDispatcher()
        val service = spyk(SlackBotService(mockClient))

        SlackBotEventHandler(eventDispatcher, service, threadRepository, incidentRepository, integrationRepository)

        // The Slack calls run synchronously in the tests (see ProjectConfig), so the threads are stored by the time the
        // dispatch returns
        fun storedThread(config: SlackBotNotificationConfig, subject: IntegrationThreadSubject, subjectId: Long) =
            threadRepository.find(config.id, subject, subjectId).shouldNotBeNull()

        given("the SlackBotEventHandler - uptime incidents") {

            `when`("a monitor goes DOWN") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(otherConfig.id, disabledConfig.id),
                    sensitiveUrl = true,
                )
                val event = HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                mockSuccessfulSlack()

                eventDispatcher.testDispatch(event)
                val downEvent = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                then("it should open a red thread in every enabled integration, reacting where it's configured") {
                    val text = formatter.toFormattedMessage(event)
                    text shouldContain "Your monitor \"${monitor.name}\" (MASKED URL) is DOWN"

                    verify(exactly = 1) { service.postMessage(globalConfig, text, RED, null) }
                    verify(exactly = 1) { service.postMessage(otherConfig, text, RED, null) }
                    verify(inverse = true) { service.postMessage(disabledConfig, any(), any(), any()) }

                    listOf(globalConfig, otherConfig).forAll { config ->
                        val thread = storedThread(config, IntegrationThreadSubject.HTTP, downEvent.id)
                        thread.ts shouldBe ROOT_TS
                        thread.rootText shouldBe text
                    }

                    verify { service.addReaction(globalConfig, ROOT_TS, "rotating_light") }
                    verify(inverse = true) { service.addReaction(otherConfig, any(), any()) }
                }
            }

            `when`("a monitor recovers after it went DOWN") {
                val monitor = createHttpMonitor(httpMonitorRepository, integrations = listOf(otherConfig.id))
                val downEvent = HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(downEvent)
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(globalConfig, IntegrationThreadSubject.HTTP, downRecord.id)
                storedThread(otherConfig, IntegrationThreadSubject.HTTP, downRecord.id)

                val upEvent = HttpMonitorUpEvent(monitor, HttpStatus.OK, 1000, downRecord)
                eventDispatcher.testDispatch(upEvent)

                then("it should reply in the thread, turn the root green and swap the reactions, keeping the thread") {
                    val downText = formatter.toFormattedMessage(downEvent)
                    val upText = formatter.toFormattedMessage(upEvent)
                    upText shouldContain "is UP (200)"

                    listOf(globalConfig, otherConfig).forAll { config ->
                        verify(exactly = 1) { service.reply(config, ROOT_TS, upText, GREEN) }
                        verify(exactly = 1) {
                            service.recolorRoot(config, ROOT_TS, formatter.strikethroughHeadline(downText), GREEN)
                        }
                        threadRepository.find(config.id, IntegrationThreadSubject.HTTP, downRecord.id)
                            .shouldNotBeNull()
                    }
                    verify(exactly = 1) { service.removeReaction(globalConfig, ROOT_TS, "rotating_light") }
                    verify(exactly = 1) { service.addReaction(globalConfig, ROOT_TS, ":white_check_mark:") }
                    verify(inverse = true) { service.removeReaction(otherConfig, any(), any()) }

                    // The colons around the name of the emoji are trimmed
                    verify(exactly = 1) {
                        mockClient.addReaction(
                            globalConfig.botToken,
                            SlackReactionRequest(globalConfig.channelId, ROOT_TS, "white_check_mark"),
                        )
                    }
                }
            }

            `when`("a monitor recovers, while Slack is still busy with the reply in its thread") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulSlack()
                val downEvent = HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                eventDispatcher.testDispatch(downEvent)
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(globalConfig, IntegrationThreadSubject.HTTP, downRecord.id)
                val pendingReply = SingleSubject.create<SlackApiResponse>()
                every { mockClient.postMessage(any(), match { request -> request.threadTs != null }) } returns
                    pendingReply

                eventDispatcher.testDispatch(HttpMonitorUpEvent(monitor, HttpStatus.OK, 1000, downRecord))

                then("its root should be updated (and its reactions swapped) only after the reply is done") {
                    // Slack updates the root on every reply, which could overwrite a parallel update of the root
                    verify(exactly = 1) { service.reply(globalConfig, ROOT_TS, any(), GREEN) }
                    verify(inverse = true) { mockClient.updateMessage(any(), any()) }
                    verify(inverse = true) { mockClient.removeReaction(any(), any()) }

                    pendingReply.onSuccess(okResponse(REPLY_TS))

                    verifyOrder {
                        mockClient.updateMessage(any(), any())
                        mockClient.removeReaction(any(), any())
                        mockClient.addReaction(
                            globalConfig.botToken,
                            match { request -> request.name == "white_check_mark" },
                        )
                    }
                }
            }

            `when`("a monitor recovers, but the reply in its thread fails") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulSlack()
                val downEvent = HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                eventDispatcher.testDispatch(downEvent)
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(globalConfig, IntegrationThreadSubject.HTTP, downRecord.id)
                every { mockClient.postMessage(any(), match { request -> request.threadTs != null }) } returns
                    Single.error(SlackApiException(SlackApiMethod.CHAT_POST_MESSAGE, SlackError.NOT_IN_CHANNEL, null))

                eventDispatcher.testDispatch(HttpMonitorUpEvent(monitor, HttpStatus.OK, 1000, downRecord))

                then("its root should still be updated, and its reactions swapped") {
                    verify(exactly = 1) {
                        service.recolorRoot(
                            globalConfig,
                            ROOT_TS,
                            formatter.strikethroughHeadline(formatter.toFormattedMessage(downEvent)),
                            GREEN,
                        )
                    }
                    verify(exactly = 1) { service.removeReaction(globalConfig, ROOT_TS, "rotating_light") }
                    verify(exactly = 1) { service.addReaction(globalConfig, ROOT_TS, ":white_check_mark:") }
                }
            }

            `when`("a monitor recovers, but its incident has no thread (e.g. the integration didn't exist back then)") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                // Only persisted, never dispatched
                databaseEventHandler.handleUptimeMonitorEvent(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                mockSuccessfulSlack()

                val upEvent = HttpMonitorUpEvent(monitor, HttpStatus.OK, 1000, downRecord)
                eventDispatcher.testDispatch(upEvent)

                then("it should post a green message to the channel instead") {
                    val upText = formatter.toFormattedMessage(upEvent)
                    verify(exactly = 1) { service.postMessage(globalConfig, upText, GREEN, null) }
                    verify(inverse = true) { service.reply(any(), any(), any(), any()) }
                    verify(inverse = true) { service.recolorRoot(any(), any(), any(), any()) }
                    verify(inverse = true) { service.removeReaction(any(), any(), any()) }
                }
            }

            `when`("an integration excludes the DOWN events, but not the UP ones") {
                val monitor = createHttpMonitor(httpMonitorRepository, integrations = listOf(excludingConfig.id))
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(globalConfig, IntegrationThreadSubject.HTTP, downRecord.id)

                val upEvent = HttpMonitorUpEvent(monitor, HttpStatus.OK, 1000, downRecord)
                eventDispatcher.testDispatch(upEvent)

                then("it should post the recovery to the channel of the excluding integration, without a thread") {
                    val upText = formatter.toFormattedMessage(upEvent)
                    verify(inverse = true) { service.postMessage(excludingConfig, any(), RED, any()) }
                    verify(exactly = 1) { service.postMessage(excludingConfig, upText, GREEN, null) }
                    verify(exactly = 1) { service.reply(globalConfig, ROOT_TS, upText, GREEN) }
                    threadRepository.find(excludingConfig.id, IntegrationThreadSubject.HTTP, downRecord.id)
                        .shouldBeNull()
                }
            }

            `when`("an integration's reactions are blank") {
                val monitor = createPushMonitor(pushMonitorRepository, integrations = listOf(excludingConfig.id))
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(PushMonitorDownEvent(monitor, "Missed heartbeat", null))
                val downRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(excludingConfig, IntegrationThreadSubject.PUSH, downRecord.id)

                eventDispatcher.testDispatch(PushMonitorUpEvent(monitor, downRecord))

                then("it should not react at all, as if there were no reactions configured") {
                    verify(exactly = 1) { service.reply(excludingConfig, ROOT_TS, any(), GREEN) }
                    verify(inverse = true) { service.addReaction(excludingConfig, any(), any()) }
                    verify(inverse = true) { service.removeReaction(excludingConfig, any(), any()) }
                }
            }

            `when`("the event that opens an incident thread cannot be found") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulSlack()

                // Dispatched without being persisted
                eventDispatcher.dispatch(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )

                then("it should still post the root and react to it, but it can't store the thread") {
                    verify(exactly = 1) { service.postMessage(globalConfig, any(), RED, null) }
                    verify { service.addReaction(globalConfig, ROOT_TS, "rotating_light") }
                    httpUptimeEventRepository.fetchByMonitorId(monitor.id).shouldBeEmpty()
                }
            }

            `when`("the root message of a thread is gone by the time the monitor recovers") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(globalConfig, IntegrationThreadSubject.HTTP, downRecord.id)
                every {
                    mockClient.postMessage(any(), match { request -> request.threadTs != null })
                } returns
                    Single.error(SlackApiException(SlackApiMethod.CHAT_POST_MESSAGE, SlackError.THREAD_NOT_FOUND, null))

                val upEvent = HttpMonitorUpEvent(monitor, HttpStatus.OK, 1000, downRecord)

                then("it should post the recovery to the channel instead, without touching the missing root") {
                    shouldNotThrowAny { eventDispatcher.testDispatch(upEvent) }

                    val upText = formatter.toFormattedMessage(upEvent)
                    verify(exactly = 1) {
                        mockClient.postMessage(
                            globalConfig.botToken,
                            match { request ->
                                request.threadTs == null && request.attachments.single().fallback == upText
                            },
                        )
                    }
                    verify(inverse = true) { service.recolorRoot(globalConfig, any(), any(), any()) }
                    verify(inverse = true) { service.removeReaction(globalConfig, any(), any()) }
                    verify(inverse = true) { service.addReaction(globalConfig, any(), ":white_check_mark:") }
                }
            }

            `when`("a push monitor goes DOWN as a part of a transaction, like its heartbeat checker or /fail does") {
                val monitor = createPushMonitor(pushMonitorRepository)
                // With a previous UP event, the DOWN one is persisted in a nested transaction (a savepoint)
                databaseEventHandler.handleUptimeMonitorEvent(PushMonitorUpEvent(monitor, null))
                val upRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                val event = PushMonitorDownEvent(monitor, "Missed heartbeat", upRecord)
                mockSuccessfulSlack()

                dslContext.transaction { config -> databaseEventHandler.handleUptimeMonitorEvent(event, config.dsl()) }
                // Dispatched only after the commit, like the push monitors do
                eventDispatcher.dispatch(event)

                then("the thread should be stored, because the event can already be found") {
                    val eventId = pushUptimeEventRepository.fetchByMonitorId(monitor.id)
                        .single { record -> record.status == UptimeStatus.DOWN }
                        .id
                    storedThread(globalConfig, IntegrationThreadSubject.PUSH, eventId)
                }
            }

            `when`("a certificate will expire, then becomes invalid, then gets renewed") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulSlack()

                val willExpireEvent = SSLWillExpireEvent(monitor, generateCertificateInfo(), null)
                eventDispatcher.testDispatch(willExpireEvent)
                val willExpireRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(globalConfig, IntegrationThreadSubject.SSL, willExpireRecord.id)

                val invalidEvent = SSLInvalidEvent(monitor, SSLValidationError("expired"), willExpireRecord)
                eventDispatcher.testDispatch(invalidEvent)
                val invalidRecord = sslEventRepository.fetchByMonitorId(monitor.id)
                    .single { record -> record.status == SslStatus.INVALID }
                val chainedThread = storedThread(globalConfig, IntegrationThreadSubject.SSL, invalidRecord.id)

                val validEvent = SSLValidEvent(monitor, generateCertificateInfo(), invalidRecord)
                eventDispatcher.testDispatch(validEvent)

                then("it should all happen in one thread, whose root turns yellow, red, and then green") {
                    val rootText = formatter.toFormattedMessage(willExpireEvent)
                    val invalidText = formatter.toFormattedMessage(invalidEvent)
                    val validText = formatter.toFormattedMessage(validEvent)

                    verify(exactly = 1) { service.postMessage(globalConfig, rootText, YELLOW, null) }
                    verify { service.addReaction(globalConfig, ROOT_TS, "rotating_light") }

                    verify(exactly = 1) { service.reply(globalConfig, ROOT_TS, invalidText, RED) }
                    verify(exactly = 1) { service.recolorRoot(globalConfig, ROOT_TS, rootText, RED) }
                    chainedThread.ts shouldBe ROOT_TS
                    chainedThread.rootText shouldBe rootText

                    verify(exactly = 1) { service.reply(globalConfig, ROOT_TS, validText, GREEN) }
                    verify(exactly = 1) {
                        service.recolorRoot(globalConfig, ROOT_TS, formatter.strikethroughHeadline(rootText), GREEN)
                    }
                    verify(exactly = 1) { service.removeReaction(globalConfig, ROOT_TS, "rotating_light") }
                    verify(exactly = 1) { service.addReaction(globalConfig, ROOT_TS, ":white_check_mark:") }

                    // Only the very first message was posted to the channel
                    verify(exactly = 1) { service.postMessage(globalConfig, any(), any(), null) }
                }
            }

            `when`("the event that continues an SSL thread cannot be found") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(SSLWillExpireEvent(monitor, generateCertificateInfo(), null))
                val willExpireRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(globalConfig, IntegrationThreadSubject.SSL, willExpireRecord.id)

                // Dispatched without being persisted
                val invalidEvent = SSLInvalidEvent(monitor, SSLValidationError("expired"), willExpireRecord)
                eventDispatcher.dispatch(invalidEvent)

                then("it should still continue the thread in Slack, without storing the new event in it") {
                    verify(exactly = 1) {
                        service.reply(globalConfig, ROOT_TS, formatter.toFormattedMessage(invalidEvent), RED)
                    }
                    verify(exactly = 1) { service.recolorRoot(globalConfig, ROOT_TS, any(), RED) }
                    sslEventRepository.fetchByMonitorId(monitor.id).single().id shouldBe willExpireRecord.id
                }
            }

            `when`("a certificate becomes invalid after it was valid") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(SSLWillExpireEvent(monitor, generateCertificateInfo(), null))
                val willExpireRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(globalConfig, IntegrationThreadSubject.SSL, willExpireRecord.id)
                eventDispatcher.testDispatch(SSLValidEvent(monitor, generateCertificateInfo(), willExpireRecord))
                val validRecord = sslEventRepository.fetchByMonitorId(monitor.id)
                    .single { record -> record.status == SslStatus.VALID }

                val invalidEvent = SSLInvalidEvent(monitor, SSLValidationError("expired"), validRecord)
                eventDispatcher.testDispatch(invalidEvent)

                then("it should open a new red thread") {
                    verify(exactly = 1) {
                        service.postMessage(globalConfig, formatter.toFormattedMessage(invalidEvent), RED, null)
                    }
                    val invalidRecord = sslEventRepository.fetchByMonitorId(monitor.id)
                        .single { record -> record.status == SslStatus.INVALID }
                    storedThread(globalConfig, IntegrationThreadSubject.SSL, invalidRecord.id)
                }
            }
        }

        given("the SlackBotEventHandler - maintenance windows") {

            `when`("a maintenance starts and ends") {
                val window = createMaintenanceWindow(
                    dslContext,
                    integrations = listOf(otherConfig.id, disabledConfig.id),
                )
                mockSuccessfulSlack()
                val startEvent = MaintenanceWindowStartEvent(window)
                eventDispatcher.dispatch(startEvent)
                storedThread(otherConfig, IntegrationThreadSubject.MAINTENANCE, window.id)

                val endEvent = MaintenanceWindowEndEvent(window)
                eventDispatcher.dispatch(endEvent)

                then("it should open a yellow thread, and resolve it in green, only in the assigned integrations") {
                    val startText = formatter.toFormattedMessage(startEvent)
                    val endText = formatter.toFormattedMessage(endEvent)
                    startText shouldContain "Maintenance \"${window.name}\" has started"

                    verify(exactly = 1) { service.postMessage(otherConfig, startText, YELLOW, null) }
                    verify(exactly = 1) { service.reply(otherConfig, ROOT_TS, endText, GREEN) }
                    verify(exactly = 1) {
                        service.recolorRoot(otherConfig, ROOT_TS, formatter.strikethroughHeadline(startText), GREEN)
                    }
                    verify(inverse = true) { service.postMessage(globalConfig, any(), any(), any()) }
                    verify(inverse = true) { service.postMessage(disabledConfig, any(), any(), any()) }
                    verify(inverse = true) { service.addReaction(any(), any(), any()) }

                    // A maintenance thread is over with the end of the maintenance
                    threadRepository.find(otherConfig.id, IntegrationThreadSubject.MAINTENANCE, window.id)
                        .shouldBeNull()
                }
            }

            `when`("a maintenance starts and ends for an integration with reactions") {
                val window = createMaintenanceWindow(dslContext, integrations = listOf(globalConfig.id))
                mockSuccessfulSlack()

                eventDispatcher.dispatch(MaintenanceWindowStartEvent(window))
                storedThread(globalConfig, IntegrationThreadSubject.MAINTENANCE, window.id)
                eventDispatcher.dispatch(MaintenanceWindowEndEvent(window))

                then("it should react to its root with the trigger reaction, and then swap it for the resolution one") {
                    verifyOrder {
                        service.addReaction(globalConfig, ROOT_TS, "rotating_light")
                        service.removeReaction(globalConfig, ROOT_TS, "rotating_light")
                        service.addReaction(globalConfig, ROOT_TS, ":white_check_mark:")
                    }
                }
            }

            `when`("a maintenance ends without a thread") {
                val window = createMaintenanceWindow(dslContext, integrations = listOf(otherConfig.id))
                mockSuccessfulSlack()
                val endEvent = MaintenanceWindowEndEvent(window)

                eventDispatcher.dispatch(endEvent)

                then("it should post a green message to the channel") {
                    verify(exactly = 1) {
                        service.postMessage(otherConfig, formatter.toFormattedMessage(endEvent), GREEN, null)
                    }
                    verify(inverse = true) { service.reply(any(), any(), any(), any()) }
                }
            }
        }

        given("the SlackBotEventHandler - incident notes") {

            fun note(
                incidentType: IncidentType,
                eventId: Long,
                content: String = "Looking into it",
                public: Boolean = true,
            ) = IncidentNoteDto(
                id = eventId,
                incidentType = incidentType,
                eventId = eventId,
                status = InvestigationStatus.INVESTIGATING,
                content = content,
                timestamp = null,
                public = public,
                createdAt = getCurrentTimestamp(),
                updatedAt = getCurrentTimestamp(),
            )

            `when`("a public note is added to an ongoing incident, and then it is edited") {
                val monitor = createHttpMonitor(httpMonitorRepository, integrations = listOf(otherConfig.id))
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                val created = IncidentNoteCreatedEvent(note(IncidentType.HTTP, downRecord.id))
                val updated = IncidentNoteUpdatedEvent(note(IncidentType.HTTP, downRecord.id, content = "Found it"))

                eventDispatcher.dispatch(created)
                eventDispatcher.dispatch(updated)

                then("both of them should be posted into the thread of every integration, without a color") {
                    // The global one mirrors only the public notes (the default), the other one all of them
                    listOf(globalConfig, otherConfig).forAll { config ->
                        verify(exactly = 1) {
                            service.postMessage(config, formatter.toFormattedMessage(created), null, ROOT_TS)
                        }
                        verify(exactly = 1) {
                            service.postMessage(config, formatter.toFormattedMessage(updated), null, ROOT_TS)
                        }
                    }
                }
            }

            `when`("a private note is added to an incident") {
                val monitor = createHttpMonitor(httpMonitorRepository, integrations = listOf(otherConfig.id))
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                val created = IncidentNoteCreatedEvent(note(IncidentType.HTTP, downRecord.id, public = false))

                eventDispatcher.dispatch(created)

                then("it should be posted only by the integration that mirrors every note") {
                    verify(exactly = 1) {
                        service.postMessage(otherConfig, formatter.toFormattedMessage(created), null, ROOT_TS)
                    }
                    verify(inverse = true) { service.postMessage(globalConfig, any(), null, ROOT_TS) }
                }
            }

            `when`("a public note is added to the incident of an integration that mirrors no notes") {
                // The excluding integration excludes only the HTTP_DOWN events, so it gets this one
                val monitor = createPushMonitor(pushMonitorRepository, integrations = listOf(excludingConfig.id))
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(PushMonitorDownEvent(monitor, "Missed heartbeat", null))
                val downRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                storedThread(excludingConfig, IntegrationThreadSubject.PUSH, downRecord.id)
                val created = IncidentNoteCreatedEvent(note(IncidentType.PUSH, downRecord.id))

                eventDispatcher.dispatch(created)

                then("it should not be posted into its thread") {
                    verify(exactly = 1) {
                        service.postMessage(globalConfig, formatter.toFormattedMessage(created), null, ROOT_TS)
                    }
                    verify(inverse = true) { service.postMessage(excludingConfig, any(), null, ROOT_TS) }
                }
            }

            `when`("a note is added to a resolved incident") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                eventDispatcher.testDispatch(HttpMonitorUpEvent(monitor, HttpStatus.OK, 1000, downRecord))
                val created = IncidentNoteCreatedEvent(note(IncidentType.HTTP, downRecord.id, content = "Post mortem"))

                eventDispatcher.dispatch(created)

                then("it should still be posted into its thread") {
                    verify(exactly = 1) {
                        service.postMessage(globalConfig, formatter.toFormattedMessage(created), null, ROOT_TS)
                    }
                }
            }

            `when`("a note is added to the invalid certificate of a chained SSL thread") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                mockSuccessfulSlack()
                eventDispatcher.testDispatch(SSLWillExpireEvent(monitor, generateCertificateInfo(), null))
                val willExpireRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()
                eventDispatcher.testDispatch(
                    SSLInvalidEvent(monitor, SSLValidationError("expired"), willExpireRecord)
                )
                val invalidRecord = sslEventRepository.fetchByMonitorId(monitor.id)
                    .single { record -> record.status == SslStatus.INVALID }
                val created = IncidentNoteCreatedEvent(note(IncidentType.SSL, invalidRecord.id))

                eventDispatcher.dispatch(created)

                then("it should be posted into the thread the expiry opened") {
                    verify(exactly = 1) {
                        service.postMessage(globalConfig, formatter.toFormattedMessage(created), null, ROOT_TS)
                    }
                }
            }

            `when`("a note is added to an incident without a thread") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                // Only persisted, never dispatched
                databaseEventHandler.handleUptimeMonitorEvent(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                mockSuccessfulSlack()

                eventDispatcher.dispatch(IncidentNoteCreatedEvent(note(IncidentType.HTTP, downRecord.id)))

                then("it should not be posted anywhere") {
                    verify(inverse = true) { service.postMessage(any(), any(), any(), any()) }
                }
            }

            `when`("the thread of an incident belongs to an integration that is disabled or not configured anymore") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                databaseEventHandler.handleUptimeMonitorEvent(
                    HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                )
                val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                listOf(disabledConfig.id, IntegrationID(IntegrationType.SLACK_BOT, "removed")).forEach { integration ->
                    threadRepository.upsert(
                        IntegrationThreadRecord()
                            .setIntegrationId(integration)
                            .setSubjectType(IntegrationThreadSubject.HTTP)
                            .setSubjectId(downRecord.id)
                            .setTs(ROOT_TS)
                            .setRootText("*Root*")
                    )
                }
                mockSuccessfulSlack()

                eventDispatcher.dispatch(IncidentNoteCreatedEvent(note(IncidentType.HTTP, downRecord.id)))

                then("it should not be posted into those threads") {
                    verify(inverse = true) { service.postMessage(any(), any(), any(), any()) }
                }
            }
        }

        given("the SlackBotEventHandler - DNS drift and Docker restart events") {

            `when`("it receives a DnsRecordsChangedEvent") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                mockSuccessfulSlack()
                val event = DnsRecordsChangedEvent(
                    monitor = monitor,
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )

                eventDispatcher.testDispatch(event)

                then("it should post a blue message to the channel") {
                    val text = formatter.toFormattedMessage(event)
                    text shouldContain "DNS records changed for monitor \"${monitor.name}\""
                    verify(exactly = 1) { service.postMessage(globalConfig, text, BLUE, null) }
                }
            }

            `when`("it receives a DockerContainerRestartedEvent") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                mockSuccessfulSlack()
                val event =
                    DockerContainerRestartedEvent(monitor = monitor, previousRestartCount = 2, currentRestartCount = 3)

                eventDispatcher.testDispatch(event)

                then("it should post a yellow message to the channel") {
                    val text = formatter.toFormattedMessage(event)
                    text shouldContain "The container of monitor \"${monitor.name}\" has been restarted"
                    verify(exactly = 1) { service.postMessage(globalConfig, text, YELLOW, null) }
                }
            }
        }

        given("the SlackBotEventHandler - error handling logic") {

            `when`("Slack refuses to post the root message") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                every { mockClient.postMessage(any(), any()) } returns
                    Single.error(SlackApiException(SlackApiMethod.CHAT_POST_MESSAGE, SlackError.NOT_IN_CHANNEL, null))

                then("it should not throw an exception, and it should not store a thread") {
                    shouldNotThrowAny {
                        eventDispatcher.testDispatch(
                            HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception(), null)
                        )
                    }
                    val downRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id)
                        .single { record -> record.status == UptimeStatus.DOWN }
                    verify { mockClient.postMessage(any(), any()) }
                    threadRepository.find(globalConfig.id, IntegrationThreadSubject.HTTP, downRecord.id)
                        .shouldBeNull()
                    verify(inverse = true) { mockClient.addReaction(any(), any()) }
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }

    private fun mockSuccessfulSlack() {
        every { mockClient.postMessage(any(), any<SlackPostMessageRequest>()) } answers {
            val request = secondArg<SlackPostMessageRequest>()
            Single.just(okResponse(if (request.threadTs == null) ROOT_TS else REPLY_TS))
        }
        every { mockClient.updateMessage(any(), any()) } returns Single.just(okResponse(ROOT_TS))
        every { mockClient.addReaction(any(), any()) } returns Single.just(okResponse(null))
        every { mockClient.removeReaction(any(), any()) } returns Single.just(okResponse(null))
    }

    private fun okResponse(ts: String?) = SlackApiResponse(ok = true, error = null, needed = null, ts = ts)

    companion object {
        private const val ROOT_TS = "1700000000.000100"
        private const val REPLY_TS = "1700000001.000200"

        private val RED = SlackColor.RED
        private val YELLOW = SlackColor.YELLOW
        private val GREEN = SlackColor.GREEN
        private val BLUE = SlackColor.BLUE
    }
}
