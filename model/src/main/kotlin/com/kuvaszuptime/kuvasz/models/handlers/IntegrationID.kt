package com.kuvaszuptime.kuvasz.models.handlers

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Singleton
import java.util.Optional

@Schema(
    type = "string",
    description = "A unique identifier for an integration, formatted as 'type:name'.",
    example = "email:my-email-integration",
)
data class IntegrationID(
    val type: IntegrationType,
    val name: String,
) {
    @JsonValue
    override fun toString(): String = "${type.identifier}:$name"

    companion object {
        @JvmStatic
        @JsonCreator
        fun jsonCreator(identifier: String): IntegrationID =
            fromString(identifier) ?: throw InvalidIntegrationIDException(identifier)

        @JsonDeserialize
        fun fromString(identifier: String): IntegrationID? =
            identifier.split(":")
                .takeIf { it.size == 2 }
                ?.let { (stringType, name) ->
                    val enumType = IntegrationType.fromIdentifier(stringType) ?: return null
                    IntegrationID(type = enumType, name = name)
                }
    }
}

class InvalidIntegrationIDException(id: String) :
    IllegalArgumentException("Invalid integration ID format: $id. Expected format is 'type:name'.")

@Singleton
class IntegrationIDTypeConverter : TypeConverter<String, IntegrationID> {
    override fun convert(
        `object`: String,
        targetType: Class<IntegrationID>,
        context: ConversionContext
    ): Optional<IntegrationID> = Optional.ofNullable(IntegrationID.fromString(`object`))
}
