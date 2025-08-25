package com.kuvaszuptime.kuvasz.controllers

import io.micronaut.http.annotation.Get
import io.micronaut.http.server.types.files.SystemFile
import io.swagger.v3.oas.annotations.Operation

interface MonitorOperationsV2 {

    @Operation(summary = "Download the export of all monitors in YAML format")
    @Get("/export/yaml")
    fun getYamlMonitorsExport(): SystemFile
}
