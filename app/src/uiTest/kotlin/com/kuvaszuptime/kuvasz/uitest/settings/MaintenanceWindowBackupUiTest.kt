package com.kuvaszuptime.kuvasz.uitest.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.settings.SettingsBackupPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.nio.file.Files

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class MaintenanceWindowBackupUiTest(
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
) : UiTestSpec() {

    // A structurally-valid backup that imports cleanly but contains no maintenance windows (parses to an empty list).
    private val emptyBackup = "{}".toByteArray()

    private fun windowByName(name: String): MaintenanceWindowRecord? =
        maintenanceWindowRepository.fetchAll().firstOrNull { it.name == name }

    init {
        "maintenance windows can be exported as a YAML file from the Settings page" {
            createMaintenanceWindow(dslContext, name = "Export Me", cron = "0 2 * * *", duration = "PT1H")

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val download = settings.exportMaintenanceWindows()

            download.suggestedFilename() shouldStartWith "kuvasz-maintenance-windows-export-"
            val content = Files.readAllBytes(download.path()).decodeToString()
            content shouldContain "maintenance-windows"
            content shouldContain "Export Me"
        }

        "the import modal opens with the destructive warning and a disabled submit until a file is chosen" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openMaintenanceWindowImportModal()
            assertThat(modal.warning).isVisible()
            assertThat(modal.dryRunToggle).isChecked()
            assertThat(modal.submitButton).isDisabled()

            modal.selectFile("backup.yml", emptyBackup)
            assertThat(modal.submitButton).isEnabled()
        }

        "the submit button label follows the dry-run toggle" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openMaintenanceWindowImportModal()
            modal.selectFile("backup.yml", emptyBackup)

            assertThat(modal.submitButton).containsText(Messages.maintenanceWindowImportPreviewButton())
            modal.setDryRun(false)
            assertThat(modal.submitButton).containsText(Messages.maintenanceWindowImportImportButton())
        }

        "a malformed YAML upload surfaces an inline error and changes nothing" {
            createMaintenanceWindow(dslContext, name = "Untouched")

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openMaintenanceWindowImportModal()
            modal.selectFile("broken.yml", "not: valid: [".toByteArray())
                .submit()

            assertThat(modal.error).isVisible()
            assertThat(modal.result).not().isVisible()
            assertThat(modal.submitButton).isVisible()
            windowByName("Untouched").shouldNotBeNull()
        }

        "a backup without any maintenance windows reports the empty result state" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openMaintenanceWindowImportModal()
            modal.selectFile("empty.yml", emptyBackup)
                .submit()

            assertThat(modal.result).isVisible()
            assertThat(modal.result).containsText(Messages.maintenanceWindowImportResultEmpty())
        }

        "a dry-run lists the windows to import/delete and the ignored monitor and integration references as badges" {
            // A window referencing a non-existing monitor and a non-configured integration: both are dropped and
            // reported during validation
            createMaintenanceWindow(
                dslContext,
                name = "With Ghosts",
                cron = "0 2 * * *",
                duration = "PT1H",
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, "ghost")),
                integrations = listOf(IntegrationID(IntegrationType.SLACK, "ghost")),
            )

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()
            val backupBytes = Files.readAllBytes(settings.exportMaintenanceWindows().path())

            // Add a window that is absent from the backup, so the preview also has a deletion to show
            createMaintenanceWindow(dslContext, name = "Stale")

            settings.navigate()
            val modal = settings.openMaintenanceWindowImportModal()
            modal.selectFile("backup.yml", backupBytes)
                .setDryRun(true)
                .submit()

            assertThat(modal.importedBadges).containsText("With Ghosts")
            assertThat(modal.deletedBadges).containsText("Stale")
            assertThat(modal.ignoredMonitorBadges).isVisible()
            assertThat(modal.ignoredMonitorBadges).containsText("http:ghost")
            assertThat(modal.ignoredIntegrationBadges).isVisible()
            assertThat(modal.ignoredIntegrationBadges).containsText("slack:ghost")

            // A dry-run must not change anything
            windowByName("With Ghosts").shouldNotBeNull()
            windowByName("Stale").shouldNotBeNull()
        }

        // Full round trip through the UI: export the current state, diverge the DB, then restore it via the modal.
        "a maintenance window backup can be dry-run previewed and then imported from the Settings page" {
            createMaintenanceWindow(dslContext, name = "Backed Up", cron = "0 2 * * *", duration = "PT1H")

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()
            val backupBytes = Files.readAllBytes(settings.exportMaintenanceWindows().path())

            // Diverge the DB from the backup: drop the backed-up window and add one absent from the backup.
            maintenanceWindowRepository.deleteById(windowByName("Backed Up").shouldNotBeNull().id)
            createMaintenanceWindow(dslContext, name = "Stale")

            settings.navigate()
            val modal = settings.openMaintenanceWindowImportModal()
            modal.selectFile("backup.yml", backupBytes)
                .setDryRun(true)
                .submit()
            assertThat(modal.result).isVisible()
            assertThat(modal.result).containsText(Messages.maintenanceWindowImportResultCountReceived())
            // The exact windows that would be imported/deleted are listed as badges
            assertThat(modal.result).containsText(Messages.maintenanceWindowImportResultImportedLabel())
            assertThat(modal.result).containsText("Backed Up")
            assertThat(modal.result).containsText(Messages.maintenanceWindowImportResultDeletedLabel())
            assertThat(modal.result).containsText("Stale")
            windowByName("Backed Up").shouldBeNull()
            windowByName("Stale").shouldNotBeNull()

            modal.setDryRun(false).submit()
            assertThat(modal.closeButton).isVisible()
            modal.close()

            windowByName("Backed Up").shouldNotBeNull()
            windowByName("Stale").shouldBeNull()
        }

        "a completed import locks the form down to a single close action" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openMaintenanceWindowImportModal()
            modal.selectFile("backup.yml", emptyBackup)
                .setDryRun(false)
                .submit()

            assertThat(modal.closeButton).isVisible()
            assertThat(modal.submitButton).not().isVisible()
            assertThat(modal.cancelButton).not().isVisible()
            assertThat(modal.fileInput).isDisabled()
            assertThat(modal.dryRunToggle).isDisabled()
        }

        "reopening the modal resets the file, the dry-run toggle and any previous result" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val firstModal = settings.openMaintenanceWindowImportModal()
            firstModal.selectFile("backup.yml", emptyBackup)
                .setDryRun(false)
                .submit()
            assertThat(firstModal.closeButton).isVisible()
            firstModal.close()

            val reopened = settings.openMaintenanceWindowImportModal()
            assertThat(reopened.result).not().isVisible()
            assertThat(reopened.error).not().isVisible()
            assertThat(reopened.dryRunToggle).isChecked()
            assertThat(reopened.fileInput).hasValue("")
            assertThat(reopened.submitButton).containsText(Messages.maintenanceWindowImportPreviewButton())
        }
    }
}
