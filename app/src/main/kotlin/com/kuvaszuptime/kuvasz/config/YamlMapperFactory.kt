package com.kuvaszuptime.kuvasz.config

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule

@Factory
class YamlMapperFactory {

    @Singleton
    fun yamlMapper(): YAMLMapper = YAMLMapper.builder()
        .addModules(kotlinModule())
        .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
}
