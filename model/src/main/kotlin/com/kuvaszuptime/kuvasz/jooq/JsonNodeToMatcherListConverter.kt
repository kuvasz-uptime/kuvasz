package com.kuvaszuptime.kuvasz.jooq

import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import org.jooq.Converter
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.treeToValue

/**
 * Same type-erasure workaround as [JsonNodeToMapConverter]: a JSONB -> List<DnsRecordMatcher> converter cannot be
 * applied during code generation, so the generated field stays a [JsonNode] and this converter is used in application
 * code instead.
 */
class JsonNodeToMatcherListConverter : Converter<JsonNode, List<DnsRecordMatcher>> {
    private val mapper = jacksonObjectMapper()

    override fun from(databaseObject: JsonNode?): List<DnsRecordMatcher> {
        return if (databaseObject == null) {
            emptyList()
        } else {
            mapper.treeToValue<List<DnsRecordMatcher>>(databaseObject)
        }
    }

    override fun to(userObject: List<DnsRecordMatcher>?): JsonNode {
        return if (userObject == null) {
            JsonNodeFactory.instance.arrayNode()
        } else {
            mapper.valueToTree(userObject)
        }
    }

    override fun fromType(): Class<JsonNode> = JsonNode::class.java

    @Suppress("UNCHECKED_CAST")
    override fun toType(): Class<List<DnsRecordMatcher>> = List::class.java as Class<List<DnsRecordMatcher>>
}
