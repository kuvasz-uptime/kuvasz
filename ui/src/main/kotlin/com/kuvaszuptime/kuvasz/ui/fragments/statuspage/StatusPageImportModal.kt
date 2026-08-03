package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.components.*
import kotlinx.html.*

private const val SLUG = "status-page"

internal fun FlowContent.statusPageImportModal(modalId: String, labelsJson: String) {
    importModal(
        modalId = modalId,
        slug = SLUG,
        alpineForm = "statusPageImportForm($labelsJson)",
        title = Messages.importStatusPageBackup(),
        warning = Messages.statusPageImportWarning(),
        fileLabel = Messages.statusPageImportFileLabel(),
        dryRunLabel = Messages.statusPageImportDryRunLabel(),
        dryRunDescription = Messages.statusPageImportDryRunDescription(),
    ) {
        flatImportResult(emptyText = Messages.statusPageImportResultEmpty()) {
            importResultBadgeList(
                itemsExpr = "result.imported",
                label = Messages.statusPageImportResultImportedLabel(),
                color = Color.GREEN_LT,
                testId = "$SLUG-import-result-imported",
            )
            importResultBadgeList(
                itemsExpr = "result.deleted",
                label = Messages.statusPageImportResultDeletedLabel(),
                color = Color.RED_LT,
                testId = "$SLUG-import-result-deleted",
            )
            importResultBadgeList(
                itemsExpr = "result.ignoredMonitors",
                label = Messages.statusPageImportResultIgnoredMonitorsLabel(),
                color = Color.YELLOW_LT,
                testId = "$SLUG-import-result-ignored-monitors",
            )
        }
    }
}
