package com.kuvaszuptime.kuvasz.ui.utils

import kotlinx.html.*

/**
 * Converting Bootstrap's own modal event to a new one, which can be listened to in alpine for example
 */
fun FlowContent.handleFormResetOnModalClose(modalId: String, eventName: String) {
    script {
        unsafe {
            +"""
            const modal = document.getElementById('$modalId')
            modal.addEventListener('hide.bs.modal', () => {
                sendWindowEvent('$eventName');
            })
            """.trimIndent()
        }
    }
}
