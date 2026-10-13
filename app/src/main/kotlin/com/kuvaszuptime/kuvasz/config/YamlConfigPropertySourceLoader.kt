package com.kuvaszuptime.kuvasz.config

import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.context.env.yaml.micronautYamlConstructor
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.io.ResourceLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.io.InputStream
import java.util.Optional

/**
 * Takes over the parsing of the YAML files passed via MICRONAUT_CONFIG_FILES (e.g. kuvasz.yml) from Micronaut's
 * built-in loader (registered in META-INF/services), to make the alias limit of SnakeYAML configurable. The default
 * limit (50) is easy to hit when anchors are used to share the defaults between a lot of monitors.
 *
 * The limit can't be a regular configuration property, because the file has to be parsed before the application
 * context exists, that's why it's only configurable via an environment variable.
 */
class YamlConfigPropertySourceLoader(
    private val getEnv: (String) -> String? = System::getenv,
) : YamlPropertySourceLoader() {

    companion object {
        const val MAX_ALIASES_ENV_VAR = "YAML_MAX_ALIASES"
        const val DEFAULT_MAX_ALIASES = 50

        private val logger: Logger = LoggerFactory.getLogger(YamlConfigPropertySourceLoader::class.java)
    }

    // Micronaut runs every registered loader against the standard files (e.g. application.yml), which would make them
    // parsed twice. MICRONAUT_CONFIG_FILES are read via read() directly, so they aren't affected by this.
    override fun readInput(resourceLoader: ResourceLoader, fileName: String): Optional<InputStream> = Optional.empty()

    override fun processInput(name: String, input: InputStream, finalMap: MutableMap<String, Any>) {
        val loaderOptions = LoaderOptions().apply { maxAliasesForCollections = resolveMaxAliases(name) }
        val dumperOptions = DumperOptions()
        Yaml(micronautYamlConstructor(), Representer(dumperOptions), dumperOptions, loaderOptions, Resolver())
            .loadAll(input)
            .filterIsInstance<Map<*, *>>()
            .forEach { processMap(finalMap, it, "") }
    }

    private fun resolveMaxAliases(name: String): Int {
        val rawValue = getEnv(MAX_ALIASES_ENV_VAR)?.takeIf { it.isNotBlank() } ?: return DEFAULT_MAX_ALIASES
        val maxAliases = rawValue.trim().toIntOrNull()?.takeIf { it > 0 }
            ?: throw ConfigurationException("$MAX_ALIASES_ENV_VAR must be a positive integer, but it was: $rawValue")
        if (maxAliases > DEFAULT_MAX_ALIASES) {
            logger.warn(
                "The alias limit of [{}] is raised to {} (default: {}) via {}. " +
                    "It protects against files that exhaust the memory with nested aliases, " +
                    "so make sure that your configuration files are only writable by trusted parties.",
                name,
                maxAliases,
                DEFAULT_MAX_ALIASES,
                MAX_ALIASES_ENV_VAR,
            )
        }
        return maxAliases
    }
}
