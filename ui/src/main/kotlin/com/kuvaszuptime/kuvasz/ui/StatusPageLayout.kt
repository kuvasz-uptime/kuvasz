package com.kuvaszuptime.kuvasz.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.layout.*
import com.kuvaszuptime.kuvasz.ui.fragments.statuspage.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

internal fun withStatusPageLayout(
    globals: AppGlobals,
    pageData: StatusPageDataDto,
): String {
    // This is a tiny hack to have a DOCTYPE notation without using kotlinx.html's own document, because it's not
    // compatible with custom attr namespaces like `x-on` or `x-bind`, etc.
    return DOCTYPE_NOTATION +
        createHTML(prettyPrint = false, xhtmlCompatible = false)
            .html {
                head {
                    val faviconsAndManifest = {
                        if (!pageData.customFaviconUrl.isNullOrBlank()) {
                            link(rel = "icon", href = "${pageData.customFaviconUrl}", type = "image/png") {
                                sizes = "32x32"
                            }
                        } else {
                            defaultFaviconsAndManifest()
                        }
                    }
                    commonHeadElements(globals.appVersion) {
                        faviconsAndManifest()
                    }
                    // Status page specific title
                    title { +pageData.title }
                    // Refresh the page every 60 seconds
                    meta(content = "60") { httpEquiv = "refresh" }
                }
                body {
                    div {
                        classes(PAGE)
                        // Main header
                        statusPageMainHeader(title = pageData.title, pageData.customLogoUrl)
                        div {
                            classes(PAGE_WRAPPER)
                            // Page header
                            div {
                                classes(PAGE_HEADER, TEXT_CENTER)
                                statusSummary(pageData)
                            }
                            // Page body
                            div {
                                classes(PAGE_BODY)
                                // Maintenance info block above the monitors
                                maintenanceBanner(pageData)
                                div {
                                    classes(CONTAINER_XL)
                                    systemStatusMonitorList(pageData)
                                }
                            }
                            // Footer
                            statusPageFooter(pageData.generatedAt)
                        }
                    }
                    commonScripts(globals.appVersion)
                }
            }
}
