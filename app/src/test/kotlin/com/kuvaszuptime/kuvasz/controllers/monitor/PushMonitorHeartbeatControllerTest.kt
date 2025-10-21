package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.heartbeat.PushMonitorFailureDetailsDto
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

@MicronautTest
class PushMonitorHeartbeatControllerTest(
    client: PushMonitorHeartbeatClient,
    monitorActions: PushMonitorActions,
) : BehaviorSpec({

    val monitor = PushMonitorRecord().apply {
        name = "irrelevant"
    }

    given("the sendHeartbeatViaPost() endpoint") {

        `when`("it is called") {
            val testSecret = "secret123"
            val actionMock = getMock(monitorActions)
            every { actionMock.updateLastHeartbeat(testSecret, any()) } returns monitor

            shouldNotThrowAny { client.sendHeartbeatViaPost(testSecret) }

            then("it should delegate to the right action") {

                verify(exactly = 1) { actionMock.updateLastHeartbeat(testSecret, any()) }
            }
        }
    }

    given("the sendHeartbeatViaGet() endpoint") {

        `when`("it is called") {
            val testSecret = "secret123"
            val actionMock = getMock(monitorActions)
            every { actionMock.updateLastHeartbeat(testSecret, any()) } returns monitor

            shouldNotThrowAny { client.sendHeartbeatViaGet(testSecret) }

            then("it should delegate to the right action") {

                verify(exactly = 1) { actionMock.updateLastHeartbeat(testSecret, any()) }
            }
        }
    }

    given("the signalFailureViaPost() endpoint") {

        `when`("it is called without a body") {
            val testSecret = "secret123"
            val actionMock = getMock(monitorActions)
            every { actionMock.signalFailure(testSecret, any()) } returns monitor

            shouldNotThrowAny { client.signalFailureViaPost(testSecret, null) }

            then("it should delegate to the right action") {

                verify(exactly = 1) { actionMock.signalFailure(testSecret, Messages.signaledExplicitError()) }
            }
        }

        `when`("it is called with an explicit error in the body") {
            val testSecret = "secret123"
            val actionMock = getMock(monitorActions)
            every { actionMock.signalFailure(testSecret, any()) } returns monitor

            shouldNotThrowAny {
                client.signalFailureViaPost(
                    testSecret,
                    PushMonitorFailureDetailsDto(
                        "explicit".repeat(100) // Needs to be redacted
                    )
                )
            }

            then("it should delegate to the right action") {
                verify(exactly = 1) {
                    actionMock.signalFailure(
                        testSecret,
                        "explicit".repeat(31) + "explici... [REDACTED]"
                    )
                }
            }
        }
    }

    given("the signalFailureViaGet() endpoint") {

        `when`("it is called") {
            val testSecret = "secret123"
            val actionMock = getMock(monitorActions)
            every { actionMock.signalFailure(testSecret, any()) } returns monitor

            shouldNotThrowAny { client.signalFailureViaGet(testSecret) }

            then("it should delegate to the right action") {

                verify(exactly = 1) { actionMock.signalFailure(testSecret, Messages.signaledExplicitError()) }
            }
        }
    }
}) {
    @MockBean(PushMonitorActions::class)
    fun mockActions(): PushMonitorActions = mockk()
}
