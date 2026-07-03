package com.kuvaszuptime.kuvasz.services.monitor.importer

import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportDto
import jakarta.inject.Singleton
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule

@Singleton
class YamlMonitorImportParser : MonitorImportParser {

    private val yamlMapper = YAMLMapper.builder()
        .addModules(kotlinModule())
        .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    @Suppress("TooGenericExceptionCaught")
    override fun parse(content: ByteArray): MonitorImportDto =
        try {
            yamlMapper.readValue(content, MonitorImportDto::class.java)
        } catch (e: Exception) {
            // Jackson 3 throws RuntimeException-based exceptions (e.g. JacksonYAMLParseException,
            // MismatchedInputException); wrap every parse-time failure into a format-agnostic exception.
            throw MonitorImportParseException("Failed to parse the uploaded YAML file: ${e.message}", e)
        }
}
