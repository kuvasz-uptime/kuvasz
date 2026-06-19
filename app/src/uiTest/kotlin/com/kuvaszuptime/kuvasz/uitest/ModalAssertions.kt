package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/**
 * Shared assertions for the "invalid input → error shown → fix → form valid again" dance that the form-validation
 * specs repeat for every monitor type and the status page.
 */

// The modal shows the [error] validation message and the Save button is disabled.
internal infix fun ModalView.shouldRejectWith(error: String) {
    assertThat(validationError(error)).isVisible()
    assertThat(saveButton).isDisabled()
}

// The [error] validation message is gone and the Save button is enabled again.
internal infix fun ModalView.shouldAcceptAfterFixing(error: String) {
    assertThat(validationError(error)).hasCount(0)
    assertThat(saveButton).isEnabled()
}
