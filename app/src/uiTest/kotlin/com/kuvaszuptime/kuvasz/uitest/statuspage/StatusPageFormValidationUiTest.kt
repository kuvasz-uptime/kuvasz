package com.kuvaszuptime.kuvasz.uitest.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.StatusPageFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.StatusPageListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

// Exercises the Alpine.js validation in the status-page create modal (slug format and required title).
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class StatusPageFormValidationUiTest : UiTestSpec() {
    init {
        "an invalid slug is flagged and blocks saving until it is corrected" {
            val modal = openCreateModal()

            modal.setTitle("Valid Title").setSlug("Invalid Slug!")
            modal shouldRejectWith Messages.errorSlugInvalid()

            modal.setSlug("valid-slug")
            modal shouldAcceptAfterFixing Messages.errorSlugInvalid()
        }

        "saving without a title surfaces the required-title error" {
            val modal = openCreateModal()

            modal.setSlug("some-slug").save()
            modal shouldRejectWith Messages.errorTitleRequired()
        }

        "saving without a slug surfaces the required-slug error" {
            val modal = openCreateModal()

            modal.setTitle("Some Title").save()
            modal shouldRejectWith Messages.errorSlugRequired()
        }
    }

    private fun openCreateModal(): StatusPageFormModal {
        val page = newPage()
        val list = StatusPageListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
