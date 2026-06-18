package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.pages.PushMonitorFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.PushMonitorListPage
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

// Exercises the Alpine.js validation in the push monitor create modal.
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class PushMonitorFormValidationUiTest : UiTestSpec() {
    init {
        "a missing name is flagged when trying to save" {
            val modal = openCreateModal()

            modal.save()
            modal shouldRejectWith Messages.errorNameRequired()
        }

        "an out-of-range heartbeat interval is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("Push Validation").setHeartbeatInterval("5")
            modal shouldRejectWith Messages.errorHeartbeatIntervalInvalid()

            modal.setHeartbeatInterval("30")
            modal shouldAcceptAfterFixing Messages.errorHeartbeatIntervalInvalid()
        }
    }

    private fun openCreateModal(): PushMonitorFormModal {
        val page = newPage()
        val list = PushMonitorListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
