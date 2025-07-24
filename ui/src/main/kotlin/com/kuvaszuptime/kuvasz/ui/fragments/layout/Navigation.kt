package com.kuvaszuptime.kuvasz.ui.fragments.layout

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.navigation(isAuthEnabled: Boolean, navbarMenuId: String) {
    header {
        classes(NAVBAR_EXPAND_MD)
        div {
            classes(COLLAPSE, NAVBAR_COLLAPSE)
            id = navbarMenuId
            div {
                classes(NAVBAR)
                div {
                    classes(CONTAINER_XL)
                    div {
                        classes(ROW, FLEX_COLUMN, FLEX_MD_ROW, FLEX_FILL, ALIGN_ITEMS_CENTER)
                        // Main nav on the left
                        div {
                            classes(CSSClass.COL)
                            ul {
                                classes(NAVBAR_NAV)
                                navItem(
                                    label = Messages.dashboard(),
                                    icon = Icon.DASHBOARD_OUTLINE,
                                    link = "/",
                                    externalLink = false,
                                )
                                navItem(
                                    label = Messages.monitors(),
                                    icon = Icon.BINOCULARS,
                                    link = "/monitors",
                                    externalLink = false
                                )
                                navItem(
                                    label = Messages.settings(),
                                    icon = Icon.SETTINGS,
                                    link = "/settings",
                                    externalLink = false
                                )
                                navItem(
                                    label = Messages.docs(),
                                    icon = Icon.BOOK,
                                    link = "https://kuvasz-uptime.dev",
                                    externalLink = true
                                )
                            }
                        }
                        // Secondary nav on the right
                        if (isAuthEnabled) {
                            div {
                                classes(CSSClass.COL, COL_MD_AUTO)
                                ul {
                                    classes(NAVBAR_NAV)
                                    navItem(
                                        label = Messages.sponsor(),
                                        icon = Icon.HEART,
                                        link = "https://kuvasz-uptime.dev/sponsoring/",
                                        externalLink = true,
                                    )
                                    navItem(
                                        label = Messages.signOut(),
                                        icon = Icon.LOGOUT_OUTLINE,
                                        link = "/auth/logout",
                                        externalLink = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun UL.navItem(label: String, icon: Icon, link: String, externalLink: Boolean = false) {
    li {
        classes(NAV_ITEM)
        a(
            href = link,
            target = if (externalLink) "_blank" else null,
        ) {
            classes(NAV_LINK)
            ariaLabel(label)
            if (externalLink) relNoOpener()
            span {
                classes(NAV_LINK_ICON, D_LG_INLINE_BLOCK)
                icon(icon)
            }
            span {
                classes(NAV_LINK_TITLE)
                +label
            }
        }
    }
}
