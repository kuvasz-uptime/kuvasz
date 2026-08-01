package com.kuvaszuptime.kuvasz.ui.fragments.maintenance

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.components.*
import kotlinx.html.*

private const val SLUG = "maintenance-window"

internal fun FlowContent.maintenanceWindowImportModal(modalId: String, labelsJson: String) {
    importModal(
        modalId = modalId,
        slug = SLUG,
        alpineForm = "maintenanceWindowImportForm($labelsJson)",
        title = Messages.importMaintenanceWindowBackup(),
        warning = Messages.maintenanceWindowImportWarning(),
        fileLabel = Messages.maintenanceWindowImportFileLabel(),
        dryRunLabel = Messages.maintenanceWindowImportDryRunLabel(),
        dryRunDescription = Messages.maintenanceWindowImportDryRunDescription(),
    ) {
        flatImportResult(emptyText = Messages.maintenanceWindowImportResultEmpty()) {
            importResultBadgeList(
                itemsExpr = "result.imported",
                label = Messages.maintenanceWindowImportResultImportedLabel(),
                color = Color.GREEN_LT,
                testId = "$SLUG-import-result-imported",
            )
            importResultBadgeList(
                itemsExpr = "result.deleted",
                label = Messages.maintenanceWindowImportResultDeletedLabel(),
                color = Color.RED_LT,
                testId = "$SLUG-import-result-deleted",
            )
            importResultBadgeList(
                itemsExpr = "result.ignoredMonitors",
                label = Messages.maintenanceWindowImportResultIgnoredMonitorsLabel(),
                color = Color.YELLOW_LT,
                testId = "$SLUG-import-result-ignored-monitors",
            )
            importResultBadgeList(
                itemsExpr = "result.ignoredIntegrations",
                label = Messages.maintenanceWindowImportResultIgnoredIntegrationsLabel(),
                color = Color.YELLOW_LT,
                testId = "$SLUG-import-result-ignored-integrations",
            )
        }
    }
}
