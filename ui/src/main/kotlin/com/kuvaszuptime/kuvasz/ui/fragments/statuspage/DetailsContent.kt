package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.statusPageDetailsContent(pageData: StatusPageDataDto) {
    div {
        id = "status-page-details-content"
        div {
            classes(HR_TEXT)
            +Messages.preview()
        }
        div {
            classes(CONTAINER, TEXT_CENTER, MB_4)
            statusSummary(pageData)
        }
        // Maintenance info block above the monitors
        maintenanceBanner(pageData)
        div {
            classes(CONTAINER_XL)
            systemStatusMonitorList(pageData)
        }
    }
}
