package com.kuvaszuptime.kuvasz.ui

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.layout.*
import com.kuvaszuptime.kuvasz.ui.fragments.statuspage.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlin.time.Duration.Companion.seconds

internal fun withStatusPageLayout(
    globals: AppGlobals,
    pageData: StatusPageDataDto,
): String {
    // This is a tiny hack to have a DOCTYPE notation without using kotlinx.html's own document, because it's not
    // compatible with custom attr namespaces like `x-on` or `x-bind`, etc.
    return DOCTYPE_NOTATION +
        createHTML(prettyPrint = false, xhtmlCompatible = false)
            .html {
                withThemeBase(pageData.themeBase)
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
                }
                body {
                    div {
                        classes(PAGE)
                        // Main header
                        statusPageMainHeader(title = pageData.title, pageData.customLogoUrl)
                        div {
                            classes(PAGE_WRAPPER)
                            div {
                                id = STATUS_PAGE_CONTENT_ID
                                hx {
                                    // The page's own URL, whatever path a reverse proxy exposes it on: the request is
                                    // told apart from a full page load by its HX-Request header
                                    get("")
                                    trigger {
                                        every(60.seconds)
                                        event(REFRESH_STATUS_PAGE_EVENT)
                                    }
                                    select("#$STATUS_PAGE_CONTENT_ID > *")
                                    // The visitor's choices about the accordions are kept on this element
                                    // across the refreshes, see applyStatusPageAccordionsState() in kuvasz.js
                                    on(
                                        "htmx:before-swap",
                                        "if (event.detail.shouldSwap) { " +
                                            "disposeComponents(this); rememberShownStatusPageAccordions(this) }",
                                    )
                                    on("htmx:after-swap", "reInitTooltips(); refreshStatusPageIncidentsModal()")
                                    // Not right after the swap: the settling of htmx would restore the rendered classes
                                    // of the accordions (they have ids), overwriting the applied state
                                    on("htmx:after-settle", "applyStatusPageAccordionsState(this)")
                                }
                                statusPageContent(pageData)
                            }
                        }
                    }
                    // Outside the refreshed part of the page, so an open modal is never swapped out
                    statusPageIncidentsModal()
                    commonScripts(globals.appVersion)
                    script(src = "/public/ext/js/htmx.2.0.10.min.js") {}
                }
            }
}

private const val STATUS_PAGE_CONTENT_ID = "status-page-content"
private const val REFRESH_STATUS_PAGE_EVENT = "refresh-status-page"

/** The part of the page that refreshes itself, see [withStatusPageLayout]. */
internal fun renderStatusPageContent(pageData: StatusPageDataDto): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).div {
        id = STATUS_PAGE_CONTENT_ID
        statusPageContent(pageData)
    }

private fun FlowContent.statusPageContent(pageData: StatusPageDataDto) {
    // Page header
    div {
        classes(PAGE_HEADER, TEXT_CENTER)
        statusSummary(pageData)
    }
    // Page body
    div {
        classes(PAGE_BODY)
        // The recent incidents and the active maintenance, right below the overall status
        statusPageAccordions(pageData)
        div {
            classes(CONTAINER_XL)
            systemStatusMonitorList(pageData)
        }
    }
    // Footer
    statusPageFooter(pageData.generatedAt)
}
