package com.kuvaszuptime.kuvasz.ui.fragments.layout

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import java.time.OffsetDateTime

internal fun FlowContent.statusPageFooter(statusPageGeneratedAt: OffsetDateTime) {
    footer {
        classes(CSSClass.FOOTER, FOOTER_TRANSPARENT)
        div {
            classes(CONTAINER_XL)
            div {
                classes(ROW, TEXT_CENTER, ALIGN_ITEMS_CENTER, FLEX_ROW_REVERSE)
                div {
                    classes(COL_LG_AUTO, MS_LG_AUTO)
                    ul {
                        classes(LIST_INLINE, LIST_INLINE_DOTS, MB_0)
                        footerListItem(
                            label = Messages.statusPageGeneratedAt(statusPageGeneratedAt.toDateTimeStringWithZone())
                        )
                    }
                }
                div {
                    classes(COL_12, COL_LG_AUTO, MT_3, MT_LG_0)
                    ul {
                        classes(LIST_INLINE, LIST_INLINE_DOTS, MB_0)
                        li {
                            classes(LIST_INLINE_ITEM)
                            span {
                                +"${Messages.poweredBy()} "
                                a(
                                    href = "https://kuvasz-uptime.dev",
                                ) {
                                    classes(LINK_SECONDARY)
                                    targetBlank()
                                    +"Kuvasz Uptime"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
