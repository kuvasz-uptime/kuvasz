package com.kuvaszuptime.kuvasz.uitest.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
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
class StatusPageBackupUiTest(private val statusPageRepository: StatusPageRepository) : UiTestSpec() {

    // A structurally-valid backup that imports cleanly but contains no status pages (parses to an empty list).
    private val emptyBackup = "{}".toByteArray()

    init {
        "status pages can be exported as a YAML file from the Settings page" {
            createStatusPage(dslContext, title = "Export Me", slug = "export-me")

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val download = settings.exportStatusPages()

            download.suggestedFilename() shouldStartWith "kuvasz-status-pages-export-"
            val content = Files.readAllBytes(download.path()).decodeToString()
            content shouldContain "status-pages"
            content shouldContain "export-me"
        }

        "the import modal opens with the destructive warning and a disabled submit until a file is chosen" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openStatusPageImportModal()
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

            val modal = settings.openStatusPageImportModal()
            modal.selectFile("backup.yml", emptyBackup)

            assertThat(modal.submitButton).containsText(Messages.statusPageImportPreviewButton())
            modal.setDryRun(false)
            assertThat(modal.submitButton).containsText(Messages.statusPageImportImportButton())
        }

        "a malformed YAML upload surfaces an inline error and changes nothing" {
            createStatusPage(dslContext, title = "Untouched", slug = "untouched")

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openStatusPageImportModal()
            modal.selectFile("broken.yml", "not: valid: [".toByteArray())
                .submit()

            assertThat(modal.error).isVisible()
            assertThat(modal.result).not().isVisible()
            assertThat(modal.submitButton).isVisible()
            statusPageRepository.findBySlug("untouched").shouldNotBeNull()
        }

        "a backup without any status pages reports the empty result state" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openStatusPageImportModal()
            modal.selectFile("empty.yml", emptyBackup)
                .submit()

            assertThat(modal.result).isVisible()
            assertThat(modal.result).containsText(Messages.statusPageImportResultEmpty())
        }

        // Full round trip through the UI: export the current state, diverge the DB, then restore it via the modal.
        "a status page backup can be dry-run previewed and then imported from the Settings page" {
            createStatusPage(dslContext, title = "Backed Up", slug = "backed-up")

            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()
            val backupBytes = Files.readAllBytes(settings.exportStatusPages().path())

            // Diverge the DB from the backup: drop the backed-up page and add one absent from the backup.
            statusPageRepository.deleteById(statusPageRepository.findBySlug("backed-up").shouldNotBeNull().id)
            createStatusPage(dslContext, title = "Stale", slug = "stale")

            settings.navigate()
            val modal = settings.openStatusPageImportModal()
            modal.selectFile("backup.yml", backupBytes)
                .setDryRun(true)
                .submit()
            assertThat(modal.result).isVisible()
            assertThat(modal.result).containsText(Messages.statusPageImportResultCountReceived())
            statusPageRepository.findBySlug("backed-up").shouldBeNull()
            statusPageRepository.findBySlug("stale").shouldNotBeNull()

            modal.setDryRun(false).submit()
            assertThat(modal.closeButton).isVisible()
            modal.close()

            statusPageRepository.findBySlug("backed-up").shouldNotBeNull()
            statusPageRepository.findBySlug("stale").shouldBeNull()
        }

        "a completed import locks the form down to a single close action" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()

            val modal = settings.openStatusPageImportModal()
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

            val firstModal = settings.openStatusPageImportModal()
            firstModal.selectFile("backup.yml", emptyBackup)
                .setDryRun(false)
                .submit()
            assertThat(firstModal.closeButton).isVisible()
            firstModal.close()

            val reopened = settings.openStatusPageImportModal()
            assertThat(reopened.result).not().isVisible()
            assertThat(reopened.error).not().isVisible()
            assertThat(reopened.dryRunToggle).isChecked()
            assertThat(reopened.fileInput).hasValue("")
            assertThat(reopened.submitButton).containsText(Messages.statusPageImportPreviewButton())
        }
    }
}
