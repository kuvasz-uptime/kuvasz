package com.kuvaszuptime.kuvasz.uitest.tcp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

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

    private fun openCreateModal(): TcpMonitorFormModal {
        val page = newPage()
        val list = TcpMonitorListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
