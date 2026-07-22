package com.kuvaszuptime.kuvasz.jooq

import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import org.jooq.Converter

/**
 * Maps the `drift_record_types` text array to [DnsRecordType]s. Unknown values are dropped rather than throwing: the
 * column is a watch list, so a type that no longer exists should narrow the list instead of breaking every check of
 * the monitor.
 */
class TextArrayToDnsRecordTypeArrayConverter : Converter<Array<String>, Array<DnsRecordType>> {

    override fun from(databaseObject: Array<String>?): Array<DnsRecordType> = databaseObject
        .orEmpty()
        .mapNotNull { value -> DnsRecordType.entries.firstOrNull { it.name == value } }
        .toTypedArray()

    override fun to(userObject: Array<DnsRecordType>?): Array<String> =
        userObject.orEmpty().map { it.name }.toTypedArray()

    override fun fromType(): Class<Array<String>> = Array<String>::class.java

    override fun toType(): Class<Array<DnsRecordType>> = Array<DnsRecordType>::class.java
}
