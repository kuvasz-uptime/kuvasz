package com.kuvaszuptime.kuvasz.ui.fragments.layout

import com.kuvaszuptime.kuvasz.ui.CSSClass.*
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
                darkModeToggle()
            }
        }
    }
}
