package com.kuvaszuptime.kuvasz.ui.fragments.layout

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.footer(appVersion: String) {
    footer {
        classes(CSSClass.FOOTER, FOOTER_TRANSPARENT, D_PRINT_NONE)
        div {
            classes(CONTAINER_XL)
            div {
                classes(ROW, TEXT_CENTER, ALIGN_ITEMS_CENTER, FLEX_ROW_REVERSE)
                div {
                    classes(COL_LG_AUTO, MS_LG_AUTO)
                    ul {
                        classes(LIST_INLINE, LIST_INLINE_DOTS, MB_0)
                        listItem(
                            label = Messages.sponsor(),
                            link = "https://ko-fi.com/L4L31DH59D",
                            externalLink = true,
                            icon = Icon.HEART,
                        )
                        listItem(
                            label = Messages.documentation(),
                            link = "https://kuvasz-uptime.dev",
                            externalLink = true,
                        )
                        listItem(
                            label = Messages.license(),
                            link = "https://github.com/kuvasz-uptime/kuvasz/blob/main/LICENSE",
                            externalLink = true,
                        )
                        listItem(
                            label = Messages.sourceCode(),
                            link = "https://github.com/kuvasz-uptime/kuvasz",
                            externalLink = true,
                        )
                    }
                }
                div {
                    classes(COL_12, COL_LG_AUTO, MT_3, MT_LG_0)
                    ul {
                        classes(LIST_INLINE, LIST_INLINE_DOTS, MB_0)
                        listItem(label = Messages.version(appVersion))
                    }
                }
            }
        }
    }
}

private fun UL.listItem(label: String, link: String? = null, externalLink: Boolean = false, icon: Icon? = null) {
    li {
        classes(LIST_INLINE_ITEM)
        if (!link.isNullOrEmpty()) {
            a(
                href = link,
            ) {
                classes(LINK_SECONDARY)
                if (externalLink) {
                    targetBlank()
                    relNoOpener()
                }
                icon?.let {
                    span {
                        classes(ME_2)
                        icon(it)
                    }
                }
                +label
            }
        } else {
            +label
        }
    }
}
