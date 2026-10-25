package com.kuvaszuptime.kuvasz.uitest.tcp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import com.microsoft.playwright.Locator
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

private const val TABLET_VIEWPORT_WIDTH = 800
private const val PHONE_VIEWPORT_WIDTH = 400

// Exercises the Alpine.js validation in the TCP monitor create modal.
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class TcpMonitorFormValidationUiTest : UiTestSpec() {
    init {
        "a missing host is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setName("TCP Validation").setPort("8080").save()
            modal shouldRejectWith Messages.errorHostRequired()

            modal.setHost("127.0.0.1")
            modal shouldAcceptAfterFixing Messages.errorHostRequired()
        }

        "a missing name is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setHost("127.0.0.1").setPort("8080").save()
            modal shouldRejectWith Messages.errorNameRequired()
        }

        "an out-of-range port is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("TCP Port").setHost("127.0.0.1").setPort("70000")
            modal shouldRejectWith Messages.errorPortInvalid()

            modal.setPort("443")
            modal shouldAcceptAfterFixing Messages.errorPortInvalid()
        }

        "the number input buttons step the port within its range" {
            val modal = openCreateModal()

            modal.setPort("443")
            modal.portIncrementButton.click()
            assertThat(modal.portInput).hasValue("444")
            modal.portDecrementButton.click()
            modal.portDecrementButton.click()
            assertThat(modal.portInput).hasValue("442")

            modal.setPort("65535")
            modal.portInput.blur()
            assertThat(modal.portIncrementButton).isDisabled()
        }

        "an accordion with an invalid field is flagged with an icon, even when it's collapsed" {
            val modal = openCreateModal()

            assertThat(modal.evaluationSettingsErrorIcon).isHidden()

            modal.setName("TCP Hidden").setHost("127.0.0.1").setPort("8080").setLatencyThreshold("0")
            modal.closeEvaluationSettings()
            assertThat(modal.evaluationSettingsErrorIcon).isVisible()

            modal.setLatencyThreshold("500")
            assertThat(modal.evaluationSettingsErrorIcon).isHidden()
        }

        "the host and the port share a row on large screens, and stack on smaller ones" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()
            val modal = list.openCreateModal()

            modal.portColumn.top() shouldBe modal.hostColumn.top()

            // The modal is only 500px wide on a tablet, which is too narrow for sharing a row
            page.setViewportSize(TABLET_VIEWPORT_WIDTH, PlaywrightSupport.VIEWPORT_HEIGHT)
            modal.portColumn.top() shouldBeGreaterThan modal.hostColumn.top()

            page.setViewportSize(PHONE_VIEWPORT_WIDTH, PlaywrightSupport.VIEWPORT_HEIGHT)
            modal.portColumn.top() shouldBeGreaterThan modal.hostColumn.top()
        }

        "an invalid latency threshold is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("TCP Latency").setHost("127.0.0.1").setPort("8080").setLatencyThreshold("0")
            modal shouldRejectWith Messages.errorLatencyThresholdInvalid()

            modal.setLatencyThreshold("500")
            modal shouldAcceptAfterFixing Messages.errorLatencyThresholdInvalid()
        }

        "an out-of-range uptime check interval is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("TCP Interval").setHost("127.0.0.1").setPort("8080").setUptimeCheckInterval("1")
            modal shouldRejectWith Messages.errorUptimeCheckIntervalInvalid()

            modal.setUptimeCheckInterval("60")
            modal shouldAcceptAfterFixing Messages.errorUptimeCheckIntervalInvalid()
        }
    }

    private fun Locator.top(): Double = boundingBox().shouldNotBeNull().y

    private fun openCreateModal(): TcpMonitorFormModal {
        val page = newPage()
        val list = TcpMonitorListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
