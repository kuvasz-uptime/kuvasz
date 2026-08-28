package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.PushoverMessageFactory
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.handlers.PushoverMessage
import com.kuvaszuptime.kuvasz.models.handlers.PushoverNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.PushoverPriority
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.reactivex.rxjava3.core.Single

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class PushoverServiceTest(
    private val client: PushoverClient,
    private val messageFactory: PushoverMessageFactory,
    private val pushoverService: PushoverService,
) : ShouldSpec({

    val apiToken = "test-api-token"
    val userKey = "test-user-key"

    fun config(
        device: String? = null,
        sound: String? = null,
        emergencyEnabled: Boolean = false,
        retrySeconds: Int = 60,
        expireSeconds: Int = 1800,
    ) = mockk<PushoverNotificationConfig>(relaxed = true) {
        every { this@mockk.apiToken } returns apiToken
        every { this@mockk.userKey } returns userKey
        every { this@mockk.device } returns device
        every { this@mockk.sound } returns sound
        every { this@mockk.emergencyEnabled } returns emergencyEnabled
        every { emergencyRetrySeconds } returns retrySeconds
        every { emergencyExpireSeconds } returns expireSeconds
    }

    val monitor = HttpMonitorRecord()
        .setId(1111)
        .setName("test_monitor")
        .setUrl("https://test.url")
        .setSensitiveUrl(false)

    val upEvent = HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null)
    val downEvent = HttpMonitorDownEvent(monitor, HttpStatus.INTERNAL_SERVER_ERROR, Exception("Boom"), null)

    context("sendTestMessage") {

        should("call the client with the test notification") {
            val mockClient = getMock(client)
            every { mockClient.sendMessage(any()) } returns Single.just("""{"status":1}""")

            val result = pushoverService.sendTestMessage(config()).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()

            verify(exactly = 1) { mockClient.sendMessage(any()) }
        }

        should("return a failed result when the client call fails") {
            val mockClient = getMock(client)
            every { mockClient.sendMessage(any()) } returns Single.error(RuntimeException("Something went wrong"))

            val result = pushoverService.sendTestMessage(config()).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("Something went wrong")

            verify(exactly = 4) { mockClient.sendMessage(any()) }
        }

        should("never escalate the test notification, even with the emergency priority enabled") {
            val mockClient = getMock(client)
            val messageSlot = slot<PushoverMessage>()
            every { mockClient.sendMessage(capture(messageSlot)) } returns Single.just("OK")

            pushoverService.sendTestMessage(config(emergencyEnabled = true)).blockingGet().success shouldBe true

            messageSlot.captured.priority shouldBe PushoverPriority.NORMAL
            messageSlot.captured.tags.shouldBeNull()
        }
    }

    context("sendEvent") {

        should("build the notification for the given event and hand it to the client") {
            val mockClient = getMock(client)
            val messageSlot = slot<PushoverMessage>()
            every { mockClient.sendMessage(capture(messageSlot)) } returns Single.just("OK")

            pushoverService.sendEvent(config(), upEvent).blockingGet() shouldBe "OK"

            val expected = messageFactory.fromUptimeEvent(upEvent)
            messageSlot.captured.title shouldBe expected.title
            messageSlot.captured.message shouldBe expected.message
        }

        should("stamp the credentials, the device and the sound of the config onto the notification") {
            val mockClient = getMock(client)
            val messageSlot = slot<PushoverMessage>()
            every { mockClient.sendMessage(capture(messageSlot)) } returns Single.just("OK")

            pushoverService.sendEvent(config(device = "iphone,desk", sound = "siren"), upEvent)
                .blockingGet() shouldBe "OK"

            with(messageSlot.captured) {
                token shouldBe apiToken
                user shouldBe userKey
                device shouldBe "iphone,desk"
                sound shouldBe "siren"
            }
        }

        should("leave the device and the sound out when the config has none") {
            val mockClient = getMock(client)
            val messageSlot = slot<PushoverMessage>()
            every { mockClient.sendMessage(capture(messageSlot)) } returns Single.just("OK")

            pushoverService.sendEvent(config(), upEvent).blockingGet() shouldBe "OK"

            messageSlot.captured.device.shouldBeNull()
            messageSlot.captured.sound.shouldBeNull()
        }
    }

    context("the escalation to the emergency priority") {

        should("escalate an outage when the config asks for it, with the retry parameters and a tag") {
            val mockClient = getMock(client)
            val messageSlot = slot<PushoverMessage>()
            every { mockClient.sendMessage(capture(messageSlot)) } returns Single.just("OK")

            val target = config(emergencyEnabled = true, retrySeconds = 90, expireSeconds = 600)
            pushoverService.sendEvent(target, downEvent).blockingGet() shouldBe "OK"

            with(messageSlot.captured) {
                priority shouldBe PushoverPriority.EMERGENCY
                retry shouldBe 90
                expire shouldBe 600
                tags shouldBe "kuvasz_uptime_1111"
            }
        }

        should("escalate an invalid certificate under a tag of its own, so it never cancels an uptime alert") {
            val mockClient = getMock(client)
            val messageSlot = slot<PushoverMessage>()
            every { mockClient.sendMessage(capture(messageSlot)) } returns Single.just("OK")

            val event = SSLInvalidEvent(monitor, SSLValidationError("Chain error"), null)
            pushoverService.sendEvent(config(emergencyEnabled = true), event).blockingGet() shouldBe "OK"

            messageSlot.captured.priority shouldBe PushoverPriority.EMERGENCY
            messageSlot.captured.tags shouldBe "kuvasz_ssl_1111"
        }

        should("leave an outage at the high priority when the config doesn't ask for the escalation") {
            val mockClient = getMock(client)
            val messageSlot = slot<PushoverMessage>()
            every { mockClient.sendMessage(capture(messageSlot)) } returns Single.just("OK")

            pushoverService.sendEvent(config(emergencyEnabled = false), downEvent).blockingGet() shouldBe "OK"

            with(messageSlot.captured) {
                priority shouldBe PushoverPriority.HIGH
                retry.shouldBeNull()
                expire.shouldBeNull()
                tags.shouldBeNull()
            }
        }

        should("never escalate an event that isn't critical") {
            val mockClient = getMock(client)
            val messageSlot = slot<PushoverMessage>()
            every { mockClient.sendMessage(capture(messageSlot)) } returns Single.just("OK")

            pushoverService.sendEvent(config(emergencyEnabled = true), upEvent).blockingGet() shouldBe "OK"

            messageSlot.captured.priority shouldBe PushoverPriority.NORMAL
            messageSlot.captured.tags.shouldBeNull()
        }

        // Maintenance and DNS drift events never carry a tag, so there would be no way to call them off
        should("never escalate the events that cannot be resolved later") {
            val mockClient = getMock(client)
            val messageSlot = slot<PushoverMessage>()
            every { mockClient.sendMessage(capture(messageSlot)) } returns Single.just("OK")
            val target = config(emergencyEnabled = true)

            pushoverService.sendEvent(target, MaintenanceWindowStartEvent(maintenanceWindow()))
                .blockingGet() shouldBe "OK"
            messageSlot.captured.priority shouldBe PushoverPriority.NORMAL
            messageSlot.captured.tags.shouldBeNull()

            pushoverService.sendEvent(
                target,
                DnsRecordsChangedEvent(
                    monitor = DnsMonitorRecord().setId(2222).setName("drift_monitor"),
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                ),
            ).blockingGet() shouldBe "OK"
            messageSlot.captured.priority shouldBe PushoverPriority.NORMAL
            messageSlot.captured.tags.shouldBeNull()
        }
    }

    context("cancelEmergency") {

        should("cancel the outstanding notifications of a recovered monitor by its reproducible tag") {
            val mockClient = getMock(client)
            every { mockClient.cancelEmergency(any(), any()) } returns Single.just("""{"status":1}""")

            pushoverService.cancelEmergency(config(emergencyEnabled = true), upEvent)
                ?.blockingGet() shouldBe """{"status":1}"""

            verify(exactly = 1) { mockClient.cancelEmergency(apiToken, "kuvasz_uptime_1111") }
        }

        should("cancel a certificate alert under the very same tag it was sent with") {
            val mockClient = getMock(client)
            every { mockClient.cancelEmergency(any(), any()) } returns Single.just("""{"status":1}""")

            val event = SSLValidEvent(monitor, generateCertificateInfo(), null)
            pushoverService.cancelEmergency(config(emergencyEnabled = true), event)
                ?.blockingGet() shouldBe """{"status":1}"""

            verify(exactly = 1) { mockClient.cancelEmergency(apiToken, "kuvasz_ssl_1111") }
        }

        should("do nothing at all when the config never escalates anything") {
            val mockClient = getMock(client)

            pushoverService.cancelEmergency(config(emergencyEnabled = false), upEvent).shouldBeNull()

            verify(exactly = 0) { mockClient.cancelEmergency(any(), any()) }
        }
    }
}) {
    @MockBean(PushoverClient::class)
    fun pushoverClient(): PushoverClient = mockk()
}

private fun maintenanceWindow() = MaintenanceWindowRecord()
    .setId(1)
    .setName("Planned upgrade")
    .setDescription("Rolling restart")
