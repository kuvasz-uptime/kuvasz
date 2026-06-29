package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class SharedMonitorActionsTest(
    private val sharedMonitorActions: SharedMonitorActions,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
) : DatabaseBehaviorSpec({

    given("getConfiguredMonitorIds") {
        `when`("there are monitors of every type") {
            val httpMonitor = createHttpMonitor(httpMonitorRepository, monitorName = "http-mon")
            val pushMonitor = createPushMonitor(pushMonitorRepository, monitorName = "push-mon")
            val icmpMonitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "icmp-mon")

            then("it maps every monitor's URN to its numeric identifier") {
                sharedMonitorActions.getConfiguredMonitorIds() shouldContainExactly mapOf(
                    MonitorID(MonitorType.HTTP_SSL, "http-mon") to httpMonitor.id,
                    MonitorID(MonitorType.PUSH, "push-mon") to pushMonitor.id,
                    MonitorID(MonitorType.ICMP, "icmp-mon") to icmpMonitor.id,
                )
            }
        }

        `when`("there are no monitors configured") {
            then("it returns an empty map") {
                sharedMonitorActions.getConfiguredMonitorIds().shouldBeEmpty()
            }
        }
    }
})
