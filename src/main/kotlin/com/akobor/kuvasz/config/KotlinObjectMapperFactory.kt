package com.akobor.kuvasz.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.jackson.JacksonConfiguration
import io.micronaut.jackson.ObjectMapperFactory
import javax.inject.Singleton

@Factory
@Replaces(ObjectMapperFactory::class)
class KotlinObjectMapperFactory : ObjectMapperFactory() {

    @Singleton
    @Replaces(ObjectMapper::class)
    override fun objectMapper(jacksonConfiguration: JacksonConfiguration?, jsonFactory: JsonFactory?): ObjectMapper =
        super.objectMapper(jacksonConfiguration, jsonFactory).apply {
            registerModule(KotlinModule())
            setSerializationInclusion(JsonInclude.Include.ALWAYS)
        }
}
