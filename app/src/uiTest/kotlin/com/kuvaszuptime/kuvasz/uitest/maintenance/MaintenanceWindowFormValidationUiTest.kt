package com.kuvaszuptime.kuvasz.uitest.maintenance

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowType
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.maintenance.MaintenanceWindowFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.maintenance.MaintenanceWindowListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * Exercises the Alpine.js validation in the maintenance-window create modal: the per-type required fields, the
 * server-backed cron format check, the ISO-8601 duration check, the duration quick-select presets, and the field/error
 * reset that happens when the window type is switched.
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class MaintenanceWindowFormValidationUiTest : UiTestSpec() {
    init {
        "the type selector toggles the relevant fields" {
            val modal = openCreateModal()

            // Manual is the default: no schedule fields at all.
            assertThat(modal.cronInput).isHidden()
            assertThat(modal.startInput).isHidden()
            assertThat(modal.durationInput).isHidden()

            modal.selectType(MaintenanceWindowType.CRON)
            assertThat(modal.cronInput).isVisible()
            assertThat(modal.durationInput).isVisible()
            assertThat(modal.startInput).isHidden()

            modal.selectType(MaintenanceWindowType.SINGLE)
            assertThat(modal.startInput).isVisible()
            assertThat(modal.durationInput).isVisible()
            assertThat(modal.cronInput).isHidden()
        }

        "enabling the global toggle hides the monitor multi-select" {
            val modal = openCreateModal()

            // Monitor-scoped by default: the multi-select is shown.
            assertThat(modal.monitorSelector).isVisible()

            modal.setGlobal(true)
            assertThat(modal.monitorSelector).isHidden()

            modal.setGlobal(false)
            assertThat(modal.monitorSelector).isVisible()
        }

        "saving a manual window without a name surfaces the required-name error" {
            val modal = openCreateModal()

            modal.save()
            modal shouldRejectWith Messages.errorMaintenanceWindowNameRequired()

            modal.setName("Has a name now")
            modal shouldAcceptAfterFixing Messages.errorMaintenanceWindowNameRequired()
        }

        "a recurring window requires a cron expression" {
            val modal = openCreateModal()

            // The on-type-change validation already surfaces the missing cron and keeps Save disabled.
            modal.selectType(MaintenanceWindowType.CRON)
                .setName("Recurring window")
                .setDuration("PT1H")

            modal shouldRejectWith Messages.errorMaintenanceWindowCronRequired()
        }

        "an invalid cron expression is flagged via the server-side check and blocks saving until corrected" {
            val modal = openCreateModal()

            modal.selectType(MaintenanceWindowType.CRON)
                .setName("Recurring window")
                .setDuration("PT1H")
                .setCron("not a cron")
                .blurCron()
            modal shouldRejectWith Messages.errorMaintenanceWindowCronInvalid()

            modal.setCron("0 2 * * *").blurCron()
            modal shouldAcceptAfterFixing Messages.errorMaintenanceWindowCronInvalid()
        }

        "a recurring window requires a duration" {
            val modal = openCreateModal()

            modal.selectType(MaintenanceWindowType.CRON)
                .setName("Recurring window")
                .setCron("0 2 * * *")
                .blurCron()

            modal shouldRejectWith Messages.errorMaintenanceWindowDurationRequired()
        }

        "an invalid ISO-8601 duration is flagged and blocks saving until corrected" {
            val modal = openCreateModal()

            modal.selectType(MaintenanceWindowType.CRON)
                .setName("Recurring window")
                .setCron("0 2 * * *")
                .blurCron()
                .setDuration("nonsense")
            modal shouldRejectWith Messages.errorMaintenanceWindowDurationInvalid()

            modal.setDuration("PT1H")
            modal shouldAcceptAfterFixing Messages.errorMaintenanceWindowDurationInvalid()
        }

        "a one-off window requires a start time" {
            val modal = openCreateModal()

            modal.selectType(MaintenanceWindowType.SINGLE)
                .setName("One-off window")
                .setDuration("PT1H")

            modal shouldRejectWith Messages.errorMaintenanceWindowStartRequired()
        }

        "a duration preset fills the input with the matching ISO value" {
            val modal = openCreateModal()
            modal.selectType(MaintenanceWindowType.CRON)

            @Suppress("MagicNumber")
            modal.durationPreset(Messages.minutesInterval(30)).click()
            assertThat(modal.durationInput).hasValue("PT30M")

            modal.durationPreset(Messages.hourInterval(1)).click()
            assertThat(modal.durationInput).hasValue("PT1H")

            modal.durationPreset(Messages.dayInterval(1)).click()
            assertThat(modal.durationInput).hasValue("PT24H")
        }

        "changing the type clears the now-irrelevant field and its hidden validation error" {
            val modal = openCreateModal()

            // Produce a (background) cron error while on the recurring type.
            modal.selectType(MaintenanceWindowType.CRON)
                .setName("Switching types")
                .setCron("not a cron")
                .blurCron()
            modal shouldRejectWith Messages.errorMaintenanceWindowCronInvalid()

            // Switching to manual hides the cron field, drops its value, and clears the now-invisible error,
            // so the form (which only needs a name for manual windows) becomes valid again.
            modal.selectType(MaintenanceWindowType.MANUAL)
            assertThat(modal.cronInput).isHidden()
            modal shouldAcceptAfterFixing Messages.errorMaintenanceWindowCronInvalid()

            // Switching back shows a cleared cron field rather than the stale invalid value.
            modal.selectType(MaintenanceWindowType.CRON)
            assertThat(modal.cronInput).isVisible()
            assertThat(modal.cronInput).hasValue("")
        }

        "switching away from the one-off type resets its start value" {
            val modal = openCreateModal()

            modal.selectType(MaintenanceWindowType.SINGLE)
                .setStart("2030-01-01T10:00")
            assertThat(modal.startInput).hasValue("2030-01-01T10:00")

            modal.selectType(MaintenanceWindowType.CRON)
            modal.selectType(MaintenanceWindowType.SINGLE)
            assertThat(modal.startInput).hasValue("")
        }
    }

    private fun openCreateModal(): MaintenanceWindowFormModal {
        val page = newPage()
        val list = MaintenanceWindowListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
