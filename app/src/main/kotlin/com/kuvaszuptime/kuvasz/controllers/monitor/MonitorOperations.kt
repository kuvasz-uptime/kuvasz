package com.kuvaszuptime.kuvasz.controllers.monitor

import io.micronaut.http.annotation.Get
import io.micronaut.http.server.types.files.SystemFile
import io.swagger.v3.oas.annotations.Operation

interface MonitorOperations {

    @Operation(summary = "Download the export of all monitors in YAML format")
    @Get("/export/yaml")
    fun getYamlMonitorsExport(): SystemFile
}
