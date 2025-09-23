package com.kuvaszuptime.kuvasz.services.export

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.micronaut.http.MediaType
import io.micronaut.http.server.types.files.SystemFile
import jakarta.inject.Singleton
import java.io.File
import java.time.Instant

@Singleton
class ExportHandler {
    private val yamlMapper = YAMLMapper()
        .registerModules(kotlinModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    /**
     * Creates a temporary YAML file from the given content map and returns it as a SystemFile.
     */
    fun <E : Any> createYamlFileFrom(fileNamePrefix: String, content: Map<String, E>): SystemFile {
        val file = File.createTempFile(TEMP_FILE_PREFIX, fileNamePrefix)
        yamlMapper.writeValue(file, content)
        val finalFileName = fileNamePrefix + Instant.now().epochSecond + YAML_EXTENSION

        return SystemFile(file, MediaType.APPLICATION_YAML_TYPE).attach(finalFileName)
    }

    companion object {
        private const val TEMP_FILE_PREFIX = "temp"
        private const val YAML_EXTENSION = ".yml"
    }
}
