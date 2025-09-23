package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.components.*
import kotlinx.html.*

internal fun FlowContent.deleteMonitorModal(
    modalId: String,
    monitorName: String,
    isDeleteDisabled: Boolean = false,
) {
    val modalMessage = if (isDeleteDisabled) {
        Messages.monitorCannotBeDeleted()
    } else {
        Messages.monitorDeleteQuestion(monitorName)
    }
    deleteModal(
        modalId = modalId,
        approvalQuestion = modalMessage,
        xOnApproval = "deleteMonitor()",
        isDeleteDisabled = isDeleteDisabled,
    )
}
