package com.kuvaszuptime.kuvasz.jooq

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import org.jooq.Converter

class TextToIntegrationIdConverter : Converter<String, IntegrationID> {

    override fun from(databaseObject: String?): IntegrationID? =
        databaseObject?.let { value -> IntegrationID.fromString(value) }

    override fun to(userObject: IntegrationID?): String? = userObject?.toString()

    override fun fromType(): Class<String> = String::class.java

    override fun toType(): Class<IntegrationID> = IntegrationID::class.java
}
