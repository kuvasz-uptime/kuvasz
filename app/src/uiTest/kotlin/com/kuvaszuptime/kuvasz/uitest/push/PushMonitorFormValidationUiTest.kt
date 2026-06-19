package com.kuvaszuptime.kuvasz.uitest.push

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.push.PushMonitorFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.push.PushMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
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

        "the client secret is auto-populated with a valid value but stays overwritable" {
            val modal = openCreateModal()

            // On open the field is pre-filled with a generated secret that already satisfies the length rule.
            modal.clientSecret.length shouldBeGreaterThanOrEqual Validation.MIN_CLIENT_SECRET_LENGTH

            // The user can replace it with their own value, which the form keeps.
            val customSecret = "a".repeat(Validation.MIN_CLIENT_SECRET_LENGTH)
            modal.setClientSecret(customSecret)
            modal.clientSecret shouldBe customSecret
        }
    }

    private fun openCreateModal(): PushMonitorFormModal {
        val page = newPage()
        val list = PushMonitorListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
