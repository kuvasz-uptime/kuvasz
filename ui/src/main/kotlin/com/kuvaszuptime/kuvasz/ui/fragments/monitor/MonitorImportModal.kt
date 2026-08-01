package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

private const val SLUG = "monitor"

internal fun FlowContent.monitorImportModal(modalId: String, labelsJson: String) {
    importModal(
        modalId = modalId,
        slug = SLUG,
        alpineForm = "monitorImportForm($labelsJson)",
        title = Messages.importMonitorBackup(),
        warning = Messages.monitorImportWarning(),
        fileLabel = Messages.monitorImportFileLabel(),
        dryRunLabel = Messages.monitorImportDryRunLabel(),
        dryRunDescription = Messages.monitorImportDryRunDescription(),
    ) {
        monitorImportResult()
    }
}

// Monitors are imported per type, so the result is a summary of what happened to each of them
private fun FlowContent.monitorImportResult() {
    p {
        xShow("result && result.perTypeResults.length === 0")
        +Messages.monitorImportResultEmpty()
    }
    div {
        xShow("result && result.perTypeResults.length > 0")
        p {
            strong { +Messages.monitorImportResultPerType() }
        }
        ul {
            templateTag {
                xFor("typeResult in (result?.perTypeResults || [])")
                liTag {
                    classes(MB_3)
                    div {
                        xText("formatTypeResult(typeResult)")
                    }
                    importResultBadgeList(
                        itemsExpr = "typeResult.imported",
                        label = Messages.monitorImportResultImportedLabel(),
                        color = Color.GREEN_LT,
                        testId = "$SLUG-import-result-imported",
                        testIdSuffixExpr = "typeResult.monitorType",
                    )
                    importResultBadgeList(
                        itemsExpr = "typeResult.deleted",
                        label = Messages.monitorImportResultDeletedLabel(),
                        color = Color.RED_LT,
                        testId = "$SLUG-import-result-deleted",
                        testIdSuffixExpr = "typeResult.monitorType",
                    )
                    importResultBadgeList(
                        itemsExpr = "typeResult.ignoredIntegrations",
                        label = Messages.monitorImportResultIgnoredIntegrationsLabel(),
                        color = Color.YELLOW_LT,
                        testId = "$SLUG-import-result-ignored-integrations",
                        testIdSuffixExpr = "typeResult.monitorType",
                    )
                }
            }
        }
    }
}
