package com.kuvaszuptime.kuvasz.uitest.pages.dns

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Alpine.js-driven create/update modal for DNS monitors.
class DnsMonitorFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#name-input")
    val hostInput: Locator get() = modal.locator("#host-input")
    val resolverHostInput: Locator get() = modal.locator("#resolverHost-input")
    val resolverPortInput: Locator get() = modal.locator("#resolverPort-input")
    val uptimeCheckIntervalInput: Locator get() = modal.locator("#uptimeCheckInterval-input")
    val latencyThresholdInput: Locator get() = modal.locator("#latencyThresholdMs-input")
    val newMatcherValueInput: Locator get() = modal.locator("#newMatcherValue-input")

    val matcherRows: Locator get() = modal.getByTestId("matcher-row")

    fun setName(value: String): DnsMonitorFormModal {
        nameInput.fill(value)
        return this
    }

    fun setHost(value: String): DnsMonitorFormModal {
        hostInput.fill(value)
        return this
    }

    fun setResolverHost(value: String): DnsMonitorFormModal {
        resolverHostInput.fill(value)
        return this
    }

    fun setResolverPort(value: String): DnsMonitorFormModal {
        resolverPortInput.fill(value)
        return this
    }

    fun setUptimeCheckInterval(value: String): DnsMonitorFormModal {
        uptimeCheckIntervalInput.fill(value)
        return this
    }

    fun setLatencyThreshold(value: String): DnsMonitorFormModal {
        latencyThresholdInput.fill(value)
        return this
    }

    val newMatcherMatchTypeSelect: Locator get() = modal.getByTestId("new-matcher-match-type")
    val addMatcherButton: Locator get() = modal.getByTestId("add-matcher-button")

    // Expands the collapsible "assertion settings" accordion that holds the record-matcher editor.
    fun openAssertionSettings(): DnsMonitorFormModal {
        if (!newMatcherValueInput.isVisible) {
            modal.getByTestId("accordion-toggle-dns-monitor-assertion-settings").click()
        }
        return this
    }

    fun setNewMatcherValue(value: String): DnsMonitorFormModal {
        newMatcherValueInput.fill(value)
        return this
    }

    fun setNewMatcherMatchType(matchType: String): DnsMonitorFormModal {
        newMatcherMatchTypeSelect.selectOption(matchType)
        return this
    }

    // Adds a record matcher through the repeating-row editor (with its default record/match type), expanding the
    // assertion-settings accordion first if it is still collapsed.
    fun addMatcher(value: String): DnsMonitorFormModal {
        openAssertionSettings()
        setNewMatcherValue(value)
        addMatcherButton.click()
        return this
    }

    fun matcherRow(value: String): Locator =
        matcherRows.filter(Locator.FilterOptions().setHasText(value))

    fun removeMatcher(value: String) {
        matcherRow(value).getByTestId("remove-matcher-button").click()
    }

    // The expected response code is a radio select-group, so every option is an input of its own.
    fun responseCodeOption(code: String): Locator = modal.locator("input[name='expectedResponseCode'][value='$code']")

    fun selectResponseCode(code: String): DnsMonitorFormModal {
        openAssertionSettings()
        // The radio input sits under its select-group label, which is what a user actually clicks.
        modal.locator("label.form-selectgroup-item")
            .filter(Locator.FilterOptions().setHasText(code))
            .click()
        return this
    }

    val driftDetectionToggle: Locator get() = modal.locator("input[name='driftDetectionEnabled']")

    // The record-type checkboxes live in an `x-if` block that Alpine only renders while drift detection is on.
    fun driftRecordTypeCheckbox(recordType: String): Locator =
        modal.locator("input[x-model='driftRecordTypes'][value='$recordType']")

    fun enableDriftDetection(): DnsMonitorFormModal {
        openAssertionSettings()
        driftDetectionToggle.check()
        return this
    }

    fun save() {
        saveButton.click()
    }
}
