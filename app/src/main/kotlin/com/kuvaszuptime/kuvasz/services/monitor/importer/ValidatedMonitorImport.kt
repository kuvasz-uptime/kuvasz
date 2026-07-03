package com.kuvaszuptime.kuvasz.services.monitor.importer

import com.kuvaszuptime.kuvasz.models.dto.importing.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.IcmpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.PushMonitorImportAdapter

data class ValidatedMonitorImport(
    val httpMonitors: List<HttpMonitorImportAdapter>,
    val pushMonitors: List<PushMonitorImportAdapter>,
    val icmpMonitors: List<IcmpMonitorImportAdapter>,
) {
    fun allAdapters(): List<Any> = httpMonitors + pushMonitors + icmpMonitors
}
