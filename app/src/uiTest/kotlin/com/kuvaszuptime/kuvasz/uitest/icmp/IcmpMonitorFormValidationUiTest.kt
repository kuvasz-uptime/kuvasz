package com.kuvaszuptime.kuvasz.uitest.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

// Exercises the Alpine.js validation in the ICMP monitor create modal.
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class IcmpMonitorFormValidationUiTest : UiTestSpec() {
    init {
        "a missing host is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setName("ICMP Validation").save()
            modal shouldRejectWith Messages.errorHostRequired()

            modal.setHost("127.0.0.1")
            modal shouldAcceptAfterFixing Messages.errorHostRequired()
        }

        "a missing name is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setHost("127.0.0.1").save()
            modal shouldRejectWith Messages.errorNameRequired()
        }

        "an out-of-range uptime check interval is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("ICMP Interval").setHost("127.0.0.1").setUptimeCheckInterval("1")
            modal shouldRejectWith Messages.errorUptimeCheckIntervalInvalid()

            modal.setUptimeCheckInterval("60")
            modal shouldAcceptAfterFixing Messages.errorUptimeCheckIntervalInvalid()
        }
    }

    private fun openCreateModal(): IcmpMonitorFormModal {
        val page = newPage()
        val list = IcmpMonitorListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
