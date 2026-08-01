package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsResolutionSnapshotDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderDnsResolutionSnapshot(snapshot: DnsResolutionSnapshotDto?): String =
    if (snapshot == null || snapshot.records.isEmpty()) {
        ""
    } else {
        createHTML(prettyPrint = false, xhtmlCompatible = false).div { dnsResolutionSnapshotBlock(snapshot) }
    }

private fun FlowContent.dnsResolutionSnapshotBlock(snapshot: DnsResolutionSnapshotDto) {
    h2 {
        classes(MB_0)
        +Messages.dnsResolvedRecordsTitle()
        span {
            classes(BADGE)
            snapshot.updatedAt.let { updatedAt ->
                tooltip(title = updatedAt.toDateTimeString())
                +updatedAt.timeAgo()
            }
        }
    }
    p {
        classes(TEXT_SECONDARY)
        +Messages.dnsResolvedRecordsDescription()
    }
    div {
        classes(ROW, ROW_CARDS, MB_3)
        div {
            classes(COL_12)
            div {
                classes(CARD)
                div {
                    classes(CARD_TABLE, TABLE_RESPONSIVE)
                    table {
                        classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                        thead {
                            tr {
                                th { +Messages.dnsRecordTypeLabel() }
                                th { +Messages.dnsResolvedRecordsColumn() }
                            }
                        }
                        tbody {
                            snapshot.records.entries
                                .filter { it.value.isNotEmpty() }
                                .sortedBy { it.key.name }
                                .forEach { (recordType, values) ->
                                    tr {
                                        td { +recordType.name }
                                        td {
                                            classes(TEXT_WRAP, TEXT_BREAK)
                                            values.forEachIndexed { index, value ->
                                                if (index > 0) br {}
                                                +value
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}
