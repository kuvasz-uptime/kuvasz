package com.kuvaszuptime.kuvasz.services.monitor.import

import com.kuvaszuptime.kuvasz.models.dto.import.MonitorImportDto
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

    override fun parse(content: ByteArray): MonitorImportDto =
        yamlMapper.readValue(content, MonitorImportDto::class.java)
}
