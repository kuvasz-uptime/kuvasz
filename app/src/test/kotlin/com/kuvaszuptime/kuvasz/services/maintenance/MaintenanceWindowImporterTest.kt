package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.MaintenanceWindowImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowExportDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest
class MaintenanceWindowImporterTest(
    private val maintenanceWindowImporter: MaintenanceWindowImporter,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
    private val maintenanceWindowScheduler: MaintenanceWindowScheduler,
    private val httpMonitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("MaintenanceWindowImporter.importMaintenanceWindowConfigs()") {

            `when`("dryRun is true") {
                val existing = createMaintenanceWindow(dslContext, name = "existing")

                val result = maintenanceWindowImporter.importMaintenanceWindowConfigs(
                    listOf(MaintenanceWindowImportAdapter(exportDto(name = "imported"))),
                    dryRun = true,
                )

                then("it returns the counts and the affected windows without persisting anything") {
                    result.receivedCnt shouldBe 1
                    result.dryRun shouldBe true
                    result.imported shouldContainExactly listOf("imported")
                    result.deleted shouldContainExactly listOf("existing")
                    result.ignoredMonitors.shouldBeEmpty()
                    result.ignoredIntegrations.shouldBeEmpty()
                    maintenanceWindowRepository.findById(existing.id).shouldNotBeNull()
                    windowByName("imported").shouldBeNull()
                }
            }

            `when`("dryRun is false") {
                val existing = createMaintenanceWindow(dslContext, name = "to-be-deleted")

                val result = maintenanceWindowImporter.importMaintenanceWindowConfigs(
                    listOf(MaintenanceWindowImportAdapter(exportDto(name = "imported"))),
                    dryRun = false,
                )

                then("it persists the backup and deletes the windows not present in it") {
                    result.receivedCnt shouldBe 1
                    result.dryRun shouldBe false
                    result.imported shouldContainExactly listOf("imported")
                    result.deleted shouldContainExactly listOf("to-be-deleted")
                    maintenanceWindowRepository.findById(existing.id).shouldBeNull()
                    windowByName("imported").shouldNotBeNull()
                }
            }

            `when`("a window references a non-existing monitor and a non-configured integration") {
                createHttpMonitor(httpMonitorRepository, monitorName = "existing-monitor")
                val existingMonitor = MonitorID(MonitorType.HTTP_SSL, "existing-monitor")
                val ghostMonitor = MonitorID(MonitorType.HTTP_SSL, "ghost")
                val ghostIntegration = IntegrationID(IntegrationType.SLACK, "ghost")

                val result = maintenanceWindowImporter.importMaintenanceWindowConfigs(
                    listOf(
                        MaintenanceWindowImportAdapter(
                            exportDto(
                                name = "with-refs",
                                monitors = setOf(existingMonitor, ghostMonitor),
                                integrations = setOf(ghostIntegration),
                            )
                        )
                    ),
                    dryRun = false,
                )

                then("the stale references are dropped, reported as ignored, and the valid monitor is kept") {
                    val persisted = windowByName("with-refs").shouldNotBeNull()
                    persisted.monitors.toList() shouldContainExactly listOf(existingMonitor)
                    persisted.integrations.toList().shouldBeEmpty()
                    result.ignoredMonitors shouldContainExactly listOf(ghostMonitor.toString())
                    result.ignoredIntegrations shouldContainExactly listOf(ghostIntegration.toString())
                }
            }
        }

        given("MaintenanceWindowImporter.importMaintenanceWindowsFromBackup()") {

            `when`("importing an enabled window as a dry-run and then for real") {
                val configs = listOf(
                    MaintenanceWindowImportAdapter(
                        exportDto(name = "scheduled", start = OffsetDateTime.now().plusDays(1).toString())
                    )
                )

                then("the window is scheduled only after the real import, not the dry-run") {
                    maintenanceWindowImporter.importMaintenanceWindowsFromBackup(configs, dryRun = true)
                    windowByName("scheduled").shouldBeNull()

                    maintenanceWindowImporter.importMaintenanceWindowsFromBackup(configs, dryRun = false)
                    val persistedId = windowByName("scheduled").shouldNotBeNull().id
                    maintenanceWindowScheduler.getScheduledWindows()[persistedId].shouldNotBeNull()
                }
            }

            `when`("the backup is empty") {
                createMaintenanceWindow(dslContext, name = "keep-me")

                val result = maintenanceWindowImporter.importMaintenanceWindowsFromBackup(emptyList(), dryRun = false)

                then("it is a no-op: nothing is deleted") {
                    result.receivedCnt shouldBe 0
                    result.imported.shouldBeEmpty()
                    result.deleted.shouldBeEmpty()
                    windowByName("keep-me").shouldNotBeNull()
                }
            }
        }
    }

    private fun windowByName(name: String) =
        maintenanceWindowRepository.fetchAll().firstOrNull { it.name == name }

    private fun exportDto(
        name: String,
        start: String? = OffsetDateTime.now().plusDays(1).toString(),
        monitors: Set<MonitorID> = emptySet(),
        integrations: Set<IntegrationID> = emptySet(),
    ) = MaintenanceWindowExportDto(
        name = name,
        description = null,
        enabled = true,
        global = true,
        showOnStatusPages = true,
        cron = null,
        start = start,
        duration = "PT1H",
        monitors = monitors,
        integrations = integrations,
    )
}
