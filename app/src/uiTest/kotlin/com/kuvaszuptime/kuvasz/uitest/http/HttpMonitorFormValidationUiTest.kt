package com.kuvaszuptime.kuvasz.uitest.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * Exercises the Alpine.js validation in the HTTP monitor create modal — the field-level checks, the server-side
 * conflict (duplicate name), and the complex custom request-headers component.
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class HttpMonitorFormValidationUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "an invalid URL is flagged and blocks saving until it is corrected" {
            val modal = openCreateModal()

            modal.setName("Validation Monitor").setUrl("not a valid url")
            modal shouldRejectWith Messages.errorInvalidUrl()

            modal.setUrl("https://example.com")
            modal shouldAcceptAfterFixing Messages.errorInvalidUrl()
        }

        "a missing name is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setUrl("https://example.com").save()
            modal shouldRejectWith Messages.errorNameRequired()
        }

        "an out-of-range uptime check interval is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("Interval Monitor").setUrl("https://example.com").setUptimeCheckInterval("1")
            modal shouldRejectWith Messages.errorUptimeCheckIntervalInvalid()

            modal.setUptimeCheckInterval("60")
            modal shouldAcceptAfterFixing Messages.errorUptimeCheckIntervalInvalid()
        }

        "creating a monitor with an already-used name surfaces the server-side conflict on the form" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Existing Monitor")
            val modal = openCreateModal()

            modal.setName("Existing Monitor").setUrl("https://example.com").save()

            // The POST returns 409, which Alpine maps onto the name field — and the modal stays open.
            modal shouldRejectWith Messages.errorNameAlreadyExists()
        }

        "the custom request-headers component validates the header name and adds/removes a header" {
            val modal = openCreateModal()
            modal.expandRequestSettings()

            // An invalid header name shows the error and keeps the add (+) button disabled.
            modal.setNewRequestHeader("Bad Header", "some-value")
            assertThat(modal.validationError(Messages.errorNewHeaderInvalid())).isVisible()
            assertThat(modal.addRequestHeaderButton).isDisabled()

            modal.setNewRequestHeaderKey("X-Custom-Header")
            assertThat(modal.validationError(Messages.errorNewHeaderInvalid())).hasCount(0)
            assertThat(modal.addRequestHeaderButton).isEnabled()

            // Adding clears the entry inputs; removing drops the row.
            modal.addRequestHeader()
            assertThat(modal.requestHeaderRow("X-Custom-Header")).isVisible()
            assertThat(modal.newRequestHeaderKeyInput).hasValue("")

            modal.removeRequestHeader("X-Custom-Header")
            assertThat(modal.requestHeaderRow("X-Custom-Header")).hasCount(0)
        }
    }

    private fun openCreateModal(): HttpMonitorFormModal {
        val page = newPage()
        val list = HttpMonitorListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
