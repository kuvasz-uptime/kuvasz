package com.kuvaszuptime.kuvasz.services.monitor.import

import com.kuvaszuptime.kuvasz.models.dto.import.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.import.IcmpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.import.PushMonitorImportAdapter

data class ValidatedMonitorImport(
    val httpMonitors: List<HttpMonitorImportAdapter>,
    val pushMonitors: List<PushMonitorImportAdapter>,
    val icmpMonitors: List<IcmpMonitorImportAdapter>,
) {
    fun allAdapters(): List<Any> = httpMonitors + pushMonitors + icmpMonitors
}
