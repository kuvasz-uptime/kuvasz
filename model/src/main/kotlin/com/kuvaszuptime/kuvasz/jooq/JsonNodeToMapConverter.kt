package com.kuvaszuptime.kuvasz.jooq

import org.jooq.Converter
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.treeToValue

/**
 * Since it's unfortunately not possible to use directly a JSONB -> Map<String, String> converter during the
 * code generation, because of the type-erasure (the compiler thinks that Map<String, String> is actually
 * Map<String, extends String>), we have to use this intermediate converter.
 */
class JsonNodeToMapConverter : Converter<JsonNode, Map<String, String>> {
    private val mapper = jacksonObjectMapper()

    override fun from(databaseObject: JsonNode?): Map<String, String> {
        return if (databaseObject == null) {
            emptyMap()
        } else {
            mapper.treeToValue<Map<String, String>>(databaseObject)
        }
    }

    override fun to(userObject: Map<String, String>?): JsonNode {
        return if (userObject == null) {
            JsonNodeFactory.instance.objectNode()
        } else {
            mapper.valueToTree(userObject)
        }
    }

    override fun fromType(): Class<JsonNode> = JsonNode::class.java
    override fun toType(): Class<Map<String, String>> = Map::class.java as Class<Map<String, String>>
}
