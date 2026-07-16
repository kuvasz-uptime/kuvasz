package com.kuvaszuptime.kuvasz.models.monitor

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord

val MonitorRecord.relativeDetailsUrl: String
    get() = when (this) {
        is HttpMonitorRecord -> "/http-monitors/${this.id}"
        is PushMonitorRecord -> "/push-monitors/${this.id}"
        is IcmpMonitorRecord -> "/icmp-monitors/${this.id}"
        is TcpMonitorRecord -> "/tcp-monitors/${this.id}"
        else -> error("Unknown monitor type: ${this::class}")
    }
