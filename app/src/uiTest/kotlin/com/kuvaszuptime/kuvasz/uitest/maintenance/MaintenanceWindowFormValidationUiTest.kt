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
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.LocalDate

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

        "enabling the global toggle hides the monitor and the category multi-selects" {
            val modal = openCreateModal()

            // Monitor-scoped by default: both selectors are shown.
            assertThat(modal.monitorSelector).isVisible()
            assertThat(modal.categorySelector).isVisible()

            modal.setGlobal(true)
            assertThat(modal.monitorSelector).isHidden()
            assertThat(modal.categorySelector).isHidden()

            modal.setGlobal(false)
            assertThat(modal.monitorSelector).isVisible()
            assertThat(modal.categorySelector).isVisible()
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
                .setStart("2030-01-01 10:00")
            assertThat(modal.startInput).hasValue("2030-01-01 10:00")

            modal.selectType(MaintenanceWindowType.CRON)
            modal.selectType(MaintenanceWindowType.SINGLE)
            assertThat(modal.startInput).hasValue("")
        }

        "a one-off window requires a time besides the start date" {
            val modal = openCreateModal()

            modal.selectType(MaintenanceWindowType.SINGLE)
                .setName("One-off window")
                .setDuration("PT1H")
                .setStart("2030-01-01")
            modal shouldRejectWith Messages.errorMaintenanceWindowStartRequired()

            modal.setStart("2030-01-01 10:00")
            modal shouldAcceptAfterFixing Messages.errorMaintenanceWindowStartRequired()
        }

        "a typed start that is not an existing day or time is flagged and blocks saving until corrected" {
            val modal = openCreateModal()

            modal.selectType(MaintenanceWindowType.SINGLE)
                .setName("One-off window")
                .setDuration("PT1H")
                .setStart("2030-02-30 10:00")
            modal shouldRejectWith Messages.errorMaintenanceWindowStartInvalid()

            modal.setStart("2030-02-28 24:00")
            modal shouldRejectWith Messages.errorMaintenanceWindowStartInvalid()

            modal.setStart("2030-02-28 10:00")
            modal shouldAcceptAfterFixing Messages.errorMaintenanceWindowStartInvalid()
        }

        "the start date and time can be picked in the datepicker" {
            val modal = openCreateModal()
            // Days that are surely in the month the calendar opens with
            val pickedDay = LocalDate.now().withDayOfMonth(1).toString()
            val typedDay = LocalDate.now().withDayOfMonth(2).toString()

            modal.selectType(MaintenanceWindowType.SINGLE)
                .setName("One-off window")
                .setDuration("PT1H")
                .pickStartDay(pickedDay)
            // The popup stays open after a day is picked, so the time can be adjusted as well
            assertThat(modal.datepicker).isVisible()
            modal.pickStartTime(hour = "09", minute = "45")
            assertThat(modal.startInput).hasValue("$pickedDay 09:45")
            modal shouldAcceptAfterFixing Messages.errorMaintenanceWindowStartRequired()

            // Picking another day keeps the picked time
            modal.datepicker.locator("[data-vc-date='$typedDay'] [data-vc-date-btn]").click()
            assertThat(modal.startInput).hasValue("$typedDay 09:45")

            // A typed start is selected in the calendar as well
            modal.setStart("$pickedDay 18:30")
            assertThat(modal.datepicker.locator("[data-vc-date='$pickedDay'][data-vc-date-selected]")).isVisible()
            assertThat(modal.datepicker.locator("[data-vc-time-input='hour'] input")).hasValue("18")
            assertThat(modal.datepicker.locator("[data-vc-time-input='minute'] input")).hasValue("30")
        }

        "the datepicker fits into the viewport and stays attached to its input when the modal is scrolled" {
            // Short enough that the popup doesn't fit below the input
            val modal = openCreateModal(viewportHeight = SHORT_VIEWPORT_HEIGHT)
            modal.selectType(MaintenanceWindowType.SINGLE)
            modal.startInput.click()
            assertThat(modal.datepicker).isVisible()

            val input = modal.startInput.boundingBox().shouldNotBeNull()
            val popup = modal.datepicker.boundingBox().shouldNotBeNull()
            // Opened above the input instead
            popup.y shouldBeGreaterThanOrEqual 0.0
            val popupBottom = popup.y + popup.height
            popupBottom shouldBeLessThanOrEqual input.y
            val offsetFromInput = popup.y - input.y

            modal.scrollBy(SCROLL_DISTANCE)
            val scrolledInput = modal.startInput.boundingBox().shouldNotBeNull()
            val scrolledPopup = modal.datepicker.boundingBox().shouldNotBeNull()
            scrolledInput.y shouldBeLessThan input.y
            val scrolledOffsetFromInput = scrolledPopup.y - scrolledInput.y
            scrolledOffsetFromInput shouldBe (offsetFromInput plusOrMinus 1.0)
        }
    }

    private fun openCreateModal(viewportHeight: Int? = null): MaintenanceWindowFormModal {
        val page = newPage()
        viewportHeight?.let { page.setViewportSize(VIEWPORT_WIDTH, it) }
        val list = MaintenanceWindowListPage(page)
        list.navigate()
        return list.openCreateModal()
    }

    companion object {
        private const val VIEWPORT_WIDTH = 1280
        private const val SHORT_VIEWPORT_HEIGHT = 600
        private const val SCROLL_DISTANCE = 100
    }
}
