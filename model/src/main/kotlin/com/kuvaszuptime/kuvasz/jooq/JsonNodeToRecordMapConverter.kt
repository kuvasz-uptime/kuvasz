package com.kuvaszuptime.kuvasz.jooq

import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsSnapshotRecords
import org.jooq.Converter
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.treeToValue

class JsonNodeToRecordMapConverter : Converter<JsonNode, DnsSnapshotRecords> {
    private val mapper = jacksonObjectMapper()

    override fun from(databaseObject: JsonNode?): DnsSnapshotRecords =
        if (databaseObject == null) emptyMap() else mapper.treeToValue<DnsSnapshotRecords>(databaseObject)

    override fun to(userObject: DnsSnapshotRecords?): JsonNode =
        if (userObject == null) JsonNodeFactory.instance.objectNode() else mapper.valueToTree(userObject)

    override fun fromType(): Class<JsonNode> = JsonNode::class.java

    @Suppress("UNCHECKED_CAST")
    override fun toType(): Class<DnsSnapshotRecords> =
        Map::class.java as Class<DnsSnapshotRecords>
}
