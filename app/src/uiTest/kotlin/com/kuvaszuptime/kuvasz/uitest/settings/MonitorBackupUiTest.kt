package com.kuvaszuptime.kuvasz.uitest.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
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
class MonitorBackupUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {

    // A structurally-valid backup that imports cleanly but contains no monitors (parses to empty lists).
    private val emptyBackup = "{}".toByteArray()

    init {
        "monitors can be exported as a YAML file from the Settings page" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Export Me")

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val download = settings.exportMonitors()

            download.suggestedFilename() shouldStartWith "kuvasz-monitors-export-"
            val content = Files.readAllBytes(download.path()).decodeToString()
            content shouldContain "http-monitors"
            content shouldContain "Export Me"
        }

        "the import modal opens with the destructive warning and a disabled submit until a file is chosen" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openImportModal()
            assertThat(modal.warning).isVisible()
            // Dry run is the safe default, and nothing can be submitted yet.
            assertThat(modal.dryRunToggle).isChecked()
            assertThat(modal.submitButton).isDisabled()

            modal.selectFile("backup.yml", emptyBackup)
            assertThat(modal.submitButton).isEnabled()
        }

        "the submit button label follows the dry-run toggle" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openImportModal()
            modal.selectFile("backup.yml", emptyBackup)

            // Dry run on -> "Preview import"; toggling it off switches to the real "Import monitors" label.
            assertThat(modal.submitButton).containsText(Messages.monitorImportPreviewButton())
            modal.setDryRun(false)
            assertThat(modal.submitButton).containsText(Messages.monitorImportImportButton())
            modal.setDryRun(true)
            assertThat(modal.submitButton).containsText(Messages.monitorImportPreviewButton())
        }

        "a malformed YAML upload surfaces an inline error and changes nothing" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Untouched")

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openImportModal()
            modal.selectFile("broken.yml", "not: valid: [".toByteArray())
                .submit()

            assertThat(modal.error).isVisible()
            assertThat(modal.result).not().isVisible()
            // The modal stays actionable and the DB is left alone.
            assertThat(modal.submitButton).isVisible()
            httpMonitorRepository.findByName("Untouched").shouldNotBeNull()
        }

        "a backup without any monitors reports the empty result state" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openImportModal()
            modal.selectFile("empty.yml", emptyBackup)
                .submit()

            assertThat(modal.result).isVisible()
            assertThat(modal.result).containsText(Messages.monitorImportResultEmpty())
        }

        "the per-type breakdown lists the imported type with its counts" {
            // Export a real backup, then add a stale monitor so the preview has both an import and a deletion to show.
            createHttpMonitor(httpMonitorRepository, monitorName = "From Backup")
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()
            val backupBytes = Files.readAllBytes(settings.exportMonitors().path())
            createHttpMonitor(httpMonitorRepository, monitorName = "Stale")

            settings.navigate()
            val modal = settings.openImportModal()
            modal.selectFile("backup.yml", backupBytes)
                .setDryRun(true)
                .submit()

            assertThat(modal.result).containsText(Messages.monitorImportResultTypeHttp())
            assertThat(modal.result).containsText(Messages.monitorImportResultCountReceived())
            assertThat(modal.result).containsText(Messages.monitorImportResultCountDeleted())
        }

        // Full round trip through the UI: export the current state, diverge the DB, then restore it via the modal.
        "a monitor backup can be dry-run previewed and then imported from the Settings page" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Backed Up")

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()
            val backupBytes = Files.readAllBytes(settings.exportMonitors().path())

            // Diverge the DB from the backup: drop the backed-up monitor and add one that is absent from the backup.
            val backedUpId = httpMonitorRepository.findByName("Backed Up").shouldNotBeNull().id
            httpMonitorRepository.deleteById(backedUpId, dslContext)
            createHttpMonitor(httpMonitorRepository, monitorName = "Stale")

            // Reload for a clean UI state, then run a dry-run preview: it must report a result but not touch the DB.
            settings.navigate()
            val modal = settings.openImportModal()
            modal.selectFile("backup.yml", backupBytes)
                .setDryRun(true)
                .submit()
            assertThat(modal.result).isVisible()
            assertThat(modal.result).containsText(Messages.monitorImportResultTypeHttp())
            httpMonitorRepository.findByName("Backed Up").shouldBeNull()
            httpMonitorRepository.findByName("Stale").shouldNotBeNull()

            // Real import: restores the backed-up monitor and deletes the stale one absent from the backup.
            modal.setDryRun(false).submit()
            assertThat(modal.closeButton).isVisible()
            modal.close()

            httpMonitorRepository.findByName("Backed Up").shouldNotBeNull()
            httpMonitorRepository.findByName("Stale").shouldBeNull()
        }

        "a completed import locks the form down to a single close action" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openImportModal()
            modal.selectFile("backup.yml", emptyBackup)
                .setDryRun(false)
                .submit()

            // Once the real import finishes only "Close" remains; the inputs are frozen so it can't be re-run.
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

            val firstModal = settings.openImportModal()
            firstModal.selectFile("backup.yml", emptyBackup)
                .setDryRun(false)
                .submit()
            assertThat(firstModal.closeButton).isVisible()
            firstModal.close()

            val reopened = settings.openImportModal()
            assertThat(reopened.result).not().isVisible()
            assertThat(reopened.error).not().isVisible()
            assertThat(reopened.dryRunToggle).isChecked()
            assertThat(reopened.fileInput).hasValue("")
            assertThat(reopened.submitButton).containsText(Messages.monitorImportPreviewButton())
        }
    }
}
