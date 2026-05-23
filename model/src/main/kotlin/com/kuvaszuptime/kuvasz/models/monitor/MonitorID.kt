package com.kuvaszuptime.kuvasz.models.monitor

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.kuvaszuptime.kuvasz.models.MonitorType
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Singleton
import tools.jackson.databind.annotation.JsonDeserialize
import java.util.Optional

@Schema(
    type = "string",
    description = "A unique identifier for a monitor, formatted as 'type:name'.",
    example = "http:My precious monitor",
)
data class MonitorID(
    val type: MonitorType,
    val name: String,
) {
    @JsonValue
    override fun toString(): String = "${type.identifier}:$name"

    companion object {
        @JvmStatic
        @JsonCreator
        fun jsonCreator(identifier: String): MonitorID =
            fromString(identifier) ?: throw InvalidMonitorIdException(identifier)

        @JsonDeserialize
        fun fromString(identifier: String): MonitorID? =
            identifier.split(":", limit = 2)
                .takeIf { it.size == 2 }
                ?.let { (stringType, name) ->
                    val enumType = MonitorType.fromIdentifier(stringType) ?: return null
                    MonitorID(type = enumType, name = name)
                }
    }
}

class InvalidMonitorIdException(id: String) :
    IllegalArgumentException("Invalid monitor ID format: $id. Expected format is 'type:name'.")

@Singleton
class MonitorIdTypeConverter : TypeConverter<String, MonitorID> {
    override fun convert(
        `object`: String,
        targetType: Class<MonitorID>,
        context: ConversionContext
    ): Optional<MonitorID> = Optional.ofNullable(MonitorID.fromString(`object`))
}
