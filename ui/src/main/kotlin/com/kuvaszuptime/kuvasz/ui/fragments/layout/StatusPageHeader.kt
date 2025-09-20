package com.kuvaszuptime.kuvasz.ui.fragments.layout

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.statusPageMainHeader(title: String, customLogoUrl: String?) {
    header {
        classes(NAVBAR, NAVBAR_EXPAND_MD)
        div {
            classes(CONTAINER_XL)
            // Navbar logo
            div {
                classes(NAVBAR_BRAND, NAVBAR_BRAND_AUTODARK, D_NONE_NAVBAR_HORIZONTAL, PE_0, PE_MD_3)
                val logoUrl = customLogoUrl?.ifBlank { null } ?: DEFAULT_SITE_LOGO_URL
                img(src = logoUrl, alt = title) {
                    classes(ME_3)
                    width = "32"
                    height = "32"
                }
                +title
            }

            div {
                classes(NAVBAR_NAV, FLEX_ROW, ORDER_MD_LAST)
                // Dark and light mode toggles
                div {
                    classes(NAV_ITEM, D_MD_FLEX)
                    a(href = "#") {
                        classes(NAV_LINK, PX_0, HIDE_THEME_DARK)
                        ariaLabel(Messages.enableDarkMode())
                        onClick = "setTheme('dark')"
                        icon(Icon.DARK_MODE)
                    }
                    a(href = "#") {
                        classes(NAV_LINK, PX_0, HIDE_THEME_LIGHT)
                        ariaLabel(Messages.enableLightMode())
                        onClick = "setTheme('light')"
                        icon(Icon.LIGHT_MODE)
                    }
                }
            }
        }
    }
}
