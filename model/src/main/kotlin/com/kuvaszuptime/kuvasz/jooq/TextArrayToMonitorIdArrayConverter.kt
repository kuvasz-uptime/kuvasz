package com.kuvaszuptime.kuvasz.jooq

import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import org.jooq.Converter

@Suppress("UseOrEmpty")
class TextArrayToMonitorIdArrayConverter :
    Converter<Array<String>, Array<MonitorID>> {

    override fun from(databaseObject: Array<String>?): Array<MonitorID> = databaseObject
        ?.let { obj ->
            obj.mapNotNull { value -> MonitorID.fromString(value) }
        }?.toTypedArray()
        ?: emptyArray()

    override fun to(userObject: Array<MonitorID>?): Array<String> {
        return userObject?.let { obj -> obj.map { value -> value.toString() } }?.toTypedArray() ?: emptyArray()
    }

    override fun fromType(): Class<Array<String>> = Array<String>::class.java

    override fun toType(): Class<Array<MonitorID>> = Array<MonitorID>::class.java
}
