package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.StatusPageImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageExportDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify

@MicronautTest
class StatusPageImporterTest(
    private val statusPageImporter: StatusPageImporter,
    private val statusPageRepository: StatusPageRepository,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val statusPageDataActions: StatusPageDataActions,
) : DatabaseBehaviorSpec() {
    init {

        given("StatusPageImporter.importStatusPageConfigs()") {

            `when`("dryRun is true") {
                val existing = createStatusPage(dslContext, slug = "existing")

                val result = statusPageImporter.importStatusPageConfigs(
                    listOf(StatusPageImportAdapter(exportDto(slug = "imported"))),
                    dryRun = true,
                )

                then("it returns the counts and the affected pages without persisting anything") {
                    result.receivedCnt shouldBe 1
                    result.dryRun shouldBe true
                    result.imported shouldContainExactly listOf("Title")
                    result.deleted shouldContainExactly listOf("Status Page")
                    result.ignoredMonitors.shouldBeEmpty()
                    statusPageRepository.findById(existing.id).shouldNotBeNull()
                    statusPageRepository.findBySlug("imported").shouldBeNull()
                }
            }

            `when`("dryRun is false") {
                val existing = createStatusPage(dslContext, slug = "to-be-deleted")

                val result = statusPageImporter.importStatusPageConfigs(
                    listOf(StatusPageImportAdapter(exportDto(slug = "imported"))),
                    dryRun = false,
                )

                then("it persists the backup and deletes the pages not present in it") {
                    result.receivedCnt shouldBe 1
                    result.dryRun shouldBe false
                    result.imported shouldContainExactly listOf("Title")
                    result.deleted shouldContainExactly listOf("Status Page")
                    statusPageRepository.findById(existing.id).shouldBeNull()
                    statusPageRepository.findBySlug("imported").shouldNotBeNull()
                }
            }

            `when`("a status page references a non-existing monitor") {
                createHttpMonitor(httpMonitorRepository, monitorName = "existing-monitor")
                val existingId = MonitorID(MonitorType.HTTP_SSL, "existing-monitor")
                val ghostId = MonitorID(MonitorType.HTTP_SSL, "ghost")

                val withRefs = exportDto(slug = "with-refs", monitors = setOf(existingId, ghostId))
                val result = statusPageImporter.importStatusPageConfigs(
                    listOf(StatusPageImportAdapter(withRefs)),
                    dryRun = false,
                )

                then("the non-existing monitor is dropped, reported as ignored, and the existing one is kept") {
                    val persisted = statusPageRepository.findBySlug("with-refs").shouldNotBeNull()
                    persisted.monitors.toList() shouldContainExactly listOf(existingId)
                    result.ignoredMonitors shouldContainExactly listOf(ghostId.toString())
                }
            }
        }

        given("StatusPageImporter.importStatusPagesFromBackup()") {

            `when`("importing as a dry-run and then for real") {
                val configs = listOf(StatusPageImportAdapter(exportDto(slug = "backup")))

                then("caches are invalidated only for the real import, not the dry-run") {
                    val dataMock = getMock(statusPageDataActions)
                    every { dataMock.invalidateAllCaches() } just Runs

                    statusPageImporter.importStatusPagesFromBackup(configs, dryRun = true)
                    verify(exactly = 0) { dataMock.invalidateAllCaches() }
                    statusPageRepository.findBySlug("backup").shouldBeNull()

                    statusPageImporter.importStatusPagesFromBackup(configs, dryRun = false)
                    verify(exactly = 1) { dataMock.invalidateAllCaches() }
                    statusPageRepository.findBySlug("backup").shouldNotBeNull()
                }
            }

            `when`("the backup is empty") {
                val existing = createStatusPage(dslContext, slug = "keep-me")

                val result = statusPageImporter.importStatusPagesFromBackup(emptyList(), dryRun = false)

                then("it is a no-op: nothing is deleted and the caches are left untouched") {
                    result.receivedCnt shouldBe 0
                    result.imported.shouldBeEmpty()
                    result.deleted.shouldBeEmpty()
                    statusPageRepository.findById(existing.id).shouldNotBeNull()
                    verify(exactly = 0) { getMock(statusPageDataActions).invalidateAllCaches() }
                }
            }
        }
    }

    private fun exportDto(
        title: String = "Title",
        slug: String,
        monitors: Set<MonitorID> = emptySet(),
    ) = StatusPageExportDto(
        title = title,
        slug = slug,
        customLogoUrl = null,
        customFaviconUrl = null,
        public = true,
        monitors = monitors,
    )

    @MockBean(StatusPageDataActions::class)
    fun statusPageDataActionsMock(): StatusPageDataActions = mockk()
}
