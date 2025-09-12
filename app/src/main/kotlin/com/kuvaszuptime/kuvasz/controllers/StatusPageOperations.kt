package com.kuvaszuptime.kuvasz.controllers

import io.micronaut.http.annotation.Get
import io.micronaut.http.server.types.files.SystemFile
import io.swagger.v3.oas.annotations.Operation

interface StatusPageOperations {

    @Operation(summary = "Download the export of all status pages in YAML format")
    @Get("/export/yaml")
    fun getYamlStatusPagesExport(): SystemFile
}
