package com.kuvaszuptime.kuvasz.uitest.pages.maintenance

import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowType
import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The Alpine.js-driven create/update modal for maintenance windows (works for the create and update modals alike).
class MaintenanceWindowFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#name-input")
    val cronInput: Locator get() = modal.locator("#cron-input")
    val startInput: Locator get() = modal.locator("#start-input")
    val durationInput: Locator get() = modal.locator("#duration-input")

    private val globalToggle: Locator get() = modal.locator("input[name='global']")

    // The TomSelect monitor multi-select; hidden for global windows (which apply to every monitor).
    val monitorSelector: Locator get() = modal.locator(".ts-wrapper")

    fun setGlobal(value: Boolean): MaintenanceWindowFormModal = apply {
        if (value) globalToggle.check() else globalToggle.uncheck()
    }

    fun typeRadio(type: MaintenanceWindowType): Locator =
        modal.locator("input[name='maintenance-window-type'][value='${type.name}']")

    // The selectgroup radios are visually hidden behind the pill styling, so they need a forced check.
    fun selectType(type: MaintenanceWindowType): MaintenanceWindowFormModal = apply {
        typeRadio(type).check(Locator.CheckOptions().setForce(true))
    }

    fun setName(value: String): MaintenanceWindowFormModal = apply { nameInput.fill(value) }

    fun setCron(value: String): MaintenanceWindowFormModal = apply { cronInput.fill(value) }

    fun setDuration(value: String): MaintenanceWindowFormModal = apply { durationInput.fill(value) }

    fun setStart(value: String): MaintenanceWindowFormModal = apply { startInput.fill(value) }

    // Cron validation only fires when the field loses focus (or on submit), mirroring the production wiring.
    fun blurCron(): MaintenanceWindowFormModal = apply { cronInput.blur() }

    // One of the quick-select duration buttons (e.g. "1 hour"), which fills the duration input with an ISO value.
    fun durationPreset(label: String): Locator =
        modal.getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName(label).setExact(true))

    fun save() {
        saveButton.click()
    }
}
