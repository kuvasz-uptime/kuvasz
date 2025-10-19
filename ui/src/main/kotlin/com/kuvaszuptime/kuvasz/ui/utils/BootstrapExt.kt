package com.kuvaszuptime.kuvasz.ui.utils

import kotlinx.html.*

/**
 * Converting Bootstrap's own modal event to a new one, which can be listened to in alpine for example
 */
fun FlowContent.handleFormResetOnModalClose(modalId: String, eventName: String) {
    val sanitizedModalId = modalId.replace("-", "")
    script {
        unsafe {
            +"""
            const $sanitizedModalId = document.getElementById('$modalId')
            $sanitizedModalId.addEventListener('hide.bs.modal', () => {
                sendWindowEvent('$eventName');
            })
            """.trimIndent()
        }
    }
}
