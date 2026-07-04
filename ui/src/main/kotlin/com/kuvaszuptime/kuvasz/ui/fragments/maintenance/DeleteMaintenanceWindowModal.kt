package com.kuvaszuptime.kuvasz.ui.fragments.maintenance

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.components.*
import kotlinx.html.*

internal fun FlowContent.deleteMaintenanceWindowModal(
    modalId: String,
    maintenanceWindowName: String,
) {
    deleteModal(
        modalId = modalId,
        approvalQuestion = Messages.maintenanceWindowDeleteQuestion(maintenanceWindowName),
        xOnApproval = "deleteMaintenanceWindow()",
        isDeleteDisabled = false,
    )
}
