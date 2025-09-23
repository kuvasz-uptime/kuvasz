package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*

internal fun FlowContent.statusPageDetailsHeading(statusPage: StatusPageDto) {
    div {
        id = "status-page-detail-heading"
        classes(COL_AUTO)

        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            div {
                classes(CSSClass.COL)
                div {
                    classes(PAGE_PRETITLE)
                    +"#${statusPage.id}"
                }
                h2 {
                    classes(PAGE_TITLE, TEXT_WRAP, TEXT_BREAK)
                    +statusPage.title.abbreviate(STATUS_PAGE_TITLE_MAX_LENGTH)
                }
                div {
                    classes(TEXT_SECONDARY)
                    ul {
                        classes(LIST_INLINE, MT_1, MB_0)
                        a(href = "/status-pages") {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            inlineStatusBadge(Messages.statusPage(), Color.BLUE_LT, Icon.HEART_RATE_MONITOR)
                        }
                        li {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            if (statusPage.public) {
                                inlineStatusBadge(Messages.public(), Color.GREEN_LT, Icon.EYE)
                            } else {
                                inlineStatusBadge(Messages.private(), Color.YELLOW_LT, Icon.EYE_OFF)
                            }
                        }
                        li {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            val statusPageUrl = "${UIDefaults.STATUS_PAGE_PATH}/${statusPage.slug}"
                            a(href = statusPageUrl) {
                                targetBlank()
                                classes(LINK_SECONDARY)
                                inlineStatusBadge(
                                    text = statusPageUrl,
                                    color = Color.DEFAULT,
                                    icon = Icon.EXTERNAL_LINK
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
