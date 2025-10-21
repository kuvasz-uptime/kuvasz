package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.statusPageCreateUpdateModal(
    modalId: String,
    statusPage: StatusPageDto?,
    globals: AppGlobals,
) {
    val serializedStatusPage: String? = statusPage?.let { objectMapper.writeValueAsString(it) }
    val serializedErrorMessages = objectMapper.writeValueAsString(
        mapOf(
            "titleRequired" to Messages.errorTitleRequired(),
            "slugRequired" to Messages.errorSlugRequired(),
            "slugInvalid" to Messages.errorSlugInvalid(),
            "slugAlreadyExists" to Messages.errorSlugAlreadyExists(),
        )
    )
    val configuredMonitors = globals.configuredMonitors()
    val serializedMonitors = objectMapper.writeValueAsString(configuredMonitors)
    val modalClosedEvent = "status-page-upsert-modal-closed"
    val monitorsSelectId = "status-page-monitors-select"
    val isReadOnlyMode = globals.editabilityState.areStatusPagesReadOnly()
    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData(
            """upsertStatusPageForm(
                |$serializedStatusPage, 
                |$serializedErrorMessages, 
                |'$monitorsSelectId', 
                |$serializedMonitors)
            """.trimMargin()
        )
        attributes["@$modalClosedEvent.window"] = "resetState()"
        tabIndex = "-1"
        role = "dialog"

        div {
            classes(MODAL_DIALOG, MODAL_LG, MODAL_DIALOG_CENTERED)
            role = "document"

            div {
                classes(MODAL_CONTENT)
                // Modal header
                div {
                    classes(MODAL_HEADER)
                    h5 {
                        classes(MODAL_TITLE)
                        if (statusPage == null) {
                            +Messages.createNewStatusPage()
                        } else if (isReadOnlyMode) {
                            +Messages.configurationOf(statusPage.title)
                        } else {
                            +Messages.updateStatusPage(statusPage.title)
                        }
                    }
                    button(type = ButtonType.button) {
                        classes(BTN_CLOSE)
                        modalCloser()
                    }
                }
                // Modal body
                div {
                    classes(MODAL_BODY, PB_0)
                    // Name
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "title",
                            label = Messages.title(),
                            placeholder = Messages.statusPageTitlePlaceholder(),
                            required = true,
                            onInput = "validateTitle()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // URL
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "slug",
                            label = Messages.slug(),
                            placeholder = Messages.statusPageSlugPlaceholder(),
                            required = true,
                            onInput = "validateSlug()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Custom logo URL
                    div {
                        classes(MB_3, ROW)
                        div {
                            classes(CSSClass.COL)
                            validatedInput(
                                propName = "customLogoUrl",
                                label = Messages.statusPageLogoUrlLabel(),
                                placeholder = Messages.statusPageLogoUrlPlaceholder(),
                                description = Messages.statusPageLogoUrlDescription(),
                                required = false,
                                disabledIf = "$isReadOnlyMode",
                            )
                        }
                        imagePreview("customLogoUrl")
                    }
                    // Custom favicon URL
                    div {
                        classes(MB_3, ROW)
                        div {
                            classes(CSSClass.COL)
                            validatedInput(
                                propName = "customFaviconUrl",
                                label = Messages.statusPageFaviconUrlLabel(),
                                placeholder = Messages.statusPageFaviconUrlPlaceholder(),
                                description = Messages.statusPageFaviconUrlDescription(),
                                required = false,
                                disabledIf = "$isReadOnlyMode",
                            )
                        }
                        imagePreview("customFaviconUrl")
                    }
                    // Visibility
                    div {
                        classes(MB_4)
                        toggleSwitch(
                            propName = "public",
                            label = Messages.public(),
                            description = Messages.statusPageVisibilityDescription(),
                            isDisabled = isReadOnlyMode,
                        )
                    }
                    // Monitors
                    div {
                        classes(MB_3)
                        formLabel(
                            label = Messages.monitors(),
                            description = Messages.statusPageMonitorsDescription(),
                            inputName = monitorsSelectId,
                            required = false,
                        )
                        monitorSelector(
                            xModelName = "selectedMonitors",
                            monitorsSelectId = monitorsSelectId,
                            isReadOnly = isReadOnlyMode,
                        )
                    }
                }
                // Modal footer
                div {
                    classes(MODAL_FOOTER)
                    a(href = "#") {
                        classes(BTN, BTN_LINK, LINK_SECONDARY)
                        modalCloser()
                        if (isReadOnlyMode) {
                            +Messages.close()
                        } else {
                            +Messages.cancel()
                        }
                    }
                    if (!isReadOnlyMode) {
                        button {
                            classes(BTN, BTN_PRIMARY, MS_AUTO)
                            xBindDisabled("hasNonNullValue(errors) || isRequestLoading")
                            xOnClick("submitForm()")
                            icon(Icon.FLOPPY)
                            +Messages.save()
                        }
                    }
                }
            }
        }
    }
    handleFormResetOnModalClose(modalId = modalId, eventName = modalClosedEvent)
}

private fun FlowContent.monitorSelector(
    xModelName: String,
    monitorsSelectId: String,
    isReadOnly: Boolean,
) {
    select {
        classes(FORM_SELECT)
        id = monitorsSelectId
        multiple = true
        xModel(xModelName)
        xInitNextTick(
            """{ new TomSelect(
                    '#$monitorsSelectId', { 
                        maxOptions: null,
                        valueField: 'value',
                        searchField: 'text',
                        plugins: ['clear_button', 'remove_button'],
                        render: {
                            option: renderMonitorOption,
                            item: renderMonitorOption,
                        },
                        onItemAdd: function(data, item) {
                            this.setTextboxValue('');
                        }
                    }
                    )}
            """.trimMargin()
        )
        if (isReadOnly) disabled = true
        templateTag {
            xFor("monitor in selectableMonitors")
            xBindKey("monitor")
            optionTag {
                xBindValue("monitor")
                xText("monitor")
                xBindSelected("selectedMonitors.includes(monitor)")
            }
        }
    }
}

private fun FlowContent.imagePreview(sourceProp: String) {
    div {
        classes(COL_AUTO, D_FLEX, JUSTIFY_CONTENT_CENTER, FLEX_COLUMN, ALIGN_ITEMS_CENTER)
        div {
            classes(IMG_PREVIEW, FORM_CONTROL)
            img {
                xBindSrc(sourceProp)
                xShow("imagePreviewState[$sourceProp] === true")
                xOnLoad("imagePreviewState[$sourceProp] = true")
                xOnError("imagePreviewState[$sourceProp] = false")
                alt = "Image preview"
            }
        }
    }
}
