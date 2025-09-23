package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.components.*
import kotlinx.html.*

internal fun FlowContent.deleteStatusPageModal(
    modalId: String,
    statusPageTitle: String,
) {
    deleteModal(
        modalId = modalId,
        approvalQuestion = Messages.statusPageDeleteQuestion(statusPageTitle),
        xOnApproval = "deleteStatusPage()",
        isDeleteDisabled = false,
    )
}
