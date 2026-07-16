package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMonitor.ICMP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.TcpMonitor.TCP_MONITOR
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class MonitorMaintenanceWindowTriggerTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
) : DatabaseBehaviorSpec() {

    private data class TriggerCase(
        val label: String,
        val type: MonitorType,
        val createMonitor: () -> Pair<Long, String>,
        val rename: (id: Long, newName: String) -> Unit,
        val delete: (id: Long) -> Unit,
    )

    init {
        val cases = listOf(
            TriggerCase(
                label = "HTTP",
                type = MonitorType.HTTP_SSL,
                createMonitor = { createHttpMonitor(httpMonitorRepository).let { it.id to it.name } },
                rename = { id, newName ->
                    dslContext.update(HTTP_MONITOR).set(HTTP_MONITOR.NAME, newName)
                        .where(HTTP_MONITOR.ID.eq(id)).execute()
                },
                delete = { id -> dslContext.deleteFrom(HTTP_MONITOR).where(HTTP_MONITOR.ID.eq(id)).execute() },
            ),
            TriggerCase(
                label = "PUSH",
                type = MonitorType.PUSH,
                createMonitor = { createPushMonitor(pushMonitorRepository).let { it.id to it.name } },
                rename = { id, newName ->
                    dslContext.update(PUSH_MONITOR).set(PUSH_MONITOR.NAME, newName)
                        .where(PUSH_MONITOR.ID.eq(id)).execute()
                },
                delete = { id -> dslContext.deleteFrom(PUSH_MONITOR).where(PUSH_MONITOR.ID.eq(id)).execute() },
            ),
            TriggerCase(
                label = "ICMP",
                type = MonitorType.ICMP,
                createMonitor = { createIcmpMonitor(icmpMonitorRepository).let { it.id to it.name } },
                rename = { id, newName ->
                    dslContext.update(ICMP_MONITOR).set(ICMP_MONITOR.NAME, newName)
                        .where(ICMP_MONITOR.ID.eq(id)).execute()
                },
                delete = { id -> dslContext.deleteFrom(ICMP_MONITOR).where(ICMP_MONITOR.ID.eq(id)).execute() },
            ),
            TriggerCase(
                label = "TCP",
                type = MonitorType.TCP,
                createMonitor = { createTcpMonitor(tcpMonitorRepository).let { it.id to it.name } },
                rename = { id, newName ->
                    dslContext.update(TCP_MONITOR).set(TCP_MONITOR.NAME, newName)
                        .where(TCP_MONITOR.ID.eq(id)).execute()
                },
                delete = { id -> dslContext.deleteFrom(TCP_MONITOR).where(TCP_MONITOR.ID.eq(id)).execute() },
            ),
        )

        cases.forEach { case ->
            given("the maintenance-window triggers for ${case.label} monitors") {

                `when`("a monitor referenced by a maintenance window is renamed") {
                    val (id, name) = case.createMonitor()
                    val otherWindow = createMaintenanceWindow(dslContext, name = "unrelated-window")
                    val window = createMaintenanceWindow(
                        dslContext,
                        monitors = listOf(MonitorID(case.type, name)),
                    )

                    case.rename(id, "renamed-monitor")

                    then("its reference is updated in the affected window and other windows are untouched") {
                        val affected = maintenanceWindowRepository.findById(window.id).shouldNotBeNull()
                        affected.monitors.toList() shouldContainExactly listOf(MonitorID(case.type, "renamed-monitor"))

                        val untouched = maintenanceWindowRepository.findById(otherWindow.id).shouldNotBeNull()
                        untouched.monitors.toList().shouldBeEmpty()
                    }
                }

                `when`("a monitor referenced by a maintenance window is deleted") {
                    val (id, name) = case.createMonitor()
                    val window = createMaintenanceWindow(
                        dslContext,
                        monitors = listOf(MonitorID(case.type, name)),
                    )

                    case.delete(id)

                    then("its reference is removed from the window") {
                        val affected = maintenanceWindowRepository.findById(window.id).shouldNotBeNull()
                        affected.monitors.toList().shouldBeEmpty()
                    }
                }
            }
        }
    }
}
