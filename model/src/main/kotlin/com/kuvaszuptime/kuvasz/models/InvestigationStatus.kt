package com.kuvaszuptime.kuvasz.models

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus

val InvestigationStatus.label: String
    get() = when (this) {
        InvestigationStatus.INVESTIGATING -> Messages.investigationStatusInvestigating()
        InvestigationStatus.IDENTIFIED -> Messages.investigationStatusIdentified()
        InvestigationStatus.RESOLVED -> Messages.investigationStatusResolved()
    }
