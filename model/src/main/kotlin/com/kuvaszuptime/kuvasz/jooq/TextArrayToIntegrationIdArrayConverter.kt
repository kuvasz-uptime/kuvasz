package com.kuvaszuptime.kuvasz.jooq

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import org.jooq.Converter

@Suppress("UseOrEmpty")
class TextArrayToIntegrationIdArrayConverter :
    Converter<Array<String>, Array<IntegrationID>> {

    override fun from(databaseObject: Array<String>?): Array<IntegrationID> = databaseObject
        ?.let { obj ->
            obj.mapNotNull { value -> IntegrationID.fromString(value) }
        }?.toTypedArray()
        ?: emptyArray()

    override fun to(userObject: Array<IntegrationID>?): Array<String> {
        return userObject?.let { obj -> obj.map { value -> value.toString() } }?.toTypedArray() ?: emptyArray()
    }

    override fun fromType(): Class<Array<String>> = Array<String>::class.java

    override fun toType(): Class<Array<IntegrationID>> = Array<IntegrationID>::class.java
}
