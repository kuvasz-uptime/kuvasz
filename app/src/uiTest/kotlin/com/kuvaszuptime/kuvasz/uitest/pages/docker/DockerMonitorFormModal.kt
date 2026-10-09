package com.kuvaszuptime.kuvasz.uitest.pages.docker

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

/**
 * The Alpine.js-driven create/update modal for Docker monitors.
 *
 * Its two type specific fields are TomSelects rather than plain inputs, so they are driven through the widget the
 * way the category field is, not by filling the `select` the widget hides.
 */
class DockerMonitorFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#docker-name-input")

    /** The connectivity-check toggle, which Docker is the only type to turn on by default. */
    val ignoreConnectivityCheckToggle: Locator
        get() = modal.locator("input[name=ignoreConnectivityCheck]")

    val restartAlertToggle: Locator get() = modal.locator("input[name='restartAlertEnabled']")

    val uptimeCheckIntervalInput: Locator get() = modal.locator("#docker-uptimeCheckInterval-input")
    val timeoutMsInput: Locator get() = modal.locator("#docker-timeoutMs-input")

    private val dockerHostField: Locator get() = modal.getByTestId("docker-host-select")
    private val containerField: Locator get() = modal.getByTestId("docker-container-select")

    /** The picked Docker host, as the chip TomSelect renders for it. */
    val selectedDockerHost: Locator get() = dockerHostField.locator(".ts-control .item")

    /** The picked container, as the chip TomSelect renders for it. */
    val selectedContainer: Locator get() = containerField.locator(".ts-control .item")

    /** The hint shown when the daemon could not produce a container listing. */
    val containerLoadFailedHint: Locator get() = containerField.locator(".form-text.text-warning")

    /** The hint shown when the instance has no Docker host configured at all. */
    val noHostsConfiguredHint: Locator get() = dockerHostField.locator(".form-text.text-danger")

    val offeredDockerHosts: List<String> get() = openedDropdownOptionsOf(dockerHostField)

    val offeredContainers: List<String> get() = openedDropdownOptionsOf(containerField)

    fun openContainerDropdown(): DockerMonitorFormModal = apply {
        containerField.locator(".ts-control").click()
    }

    fun containerOption(name: String): Locator =
        containerField.locator(".ts-dropdown .option").filter(Locator.FilterOptions().setHasText(name))

    fun setName(value: String): DockerMonitorFormModal {
        nameInput.fill(value)
        return this
    }

    fun setCategory(value: String): DockerMonitorFormModal {
        fillCategory(value)
        return this
    }

    fun setDockerHost(value: String): DockerMonitorFormModal {
        pickFromDropdown(dockerHostField, value)
        return this
    }

    /** The container field takes a typed-in value too, since the listing is best effort. */
    fun setContainer(value: String): DockerMonitorFormModal {
        typeAndCommit(containerField, value)
        return this
    }

    fun enableRestartAlerts(): DockerMonitorFormModal {
        restartAlertToggle.check()
        return this
    }

    fun setUptimeCheckInterval(value: String): DockerMonitorFormModal {
        uptimeCheckIntervalInput.fill(value)
        return this
    }

    fun setTimeoutMs(value: String): DockerMonitorFormModal {
        timeoutMsInput.fill(value)
        return this
    }

    fun save() {
        saveButton.click()
    }

    /**
     * The container options only arrive once the listing resolves, so the first one is awaited before they are
     * read - `allTextContents()` does not auto-wait on its own.
     */
    private fun openedDropdownOptionsOf(field: Locator): List<String> {
        field.locator(".ts-control").click()
        val options = field.locator(".ts-dropdown .option")
        options.first().waitFor()
        return options.allTextContents().map { it.trim() }
    }

}
