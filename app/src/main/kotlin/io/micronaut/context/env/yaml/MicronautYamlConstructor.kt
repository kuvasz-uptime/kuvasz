package io.micronaut.context.env.yaml

import org.yaml.snakeyaml.constructor.BaseConstructor

/**
 * Exposes Micronaut's package-private YAML constructor, so the configuration files are constructed exactly the same
 * way as by the built-in loader (e.g. timestamps become java.time values)
 */
internal fun micronautYamlConstructor(): BaseConstructor = CustomSafeConstructor()
