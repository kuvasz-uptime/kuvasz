package com.kuvaszuptime.kuvasz.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.context.exceptions.ConfigurationException
import org.yaml.snakeyaml.error.YAMLException
import java.time.OffsetDateTime

class YamlConfigPropertySourceLoaderTest : BehaviorSpec(
    {
        given("a YAML configuration file with more aliases than the default limit") {
            val yaml = monitorsWithSharedDefaults(count = DEFAULT_LIMIT_EXCEEDED)

            `when`("the limit is not configured") {
                then("it should fail with the default limit") {
                    val exception = shouldThrow<YAMLException> { loader().load(yaml) }
                    exception.message shouldContain "max=50"
                }
            }

            `when`("the limit is raised via the environment variable") {
                then("it should be parsed") {
                    val properties = loader(env = mapOf("YAML_MAX_ALIASES" to "100")).load(yaml)
                    properties["monitors.m50.uptime-check-interval"] shouldBe 180
                }
            }

            `when`("the environment variable is blank") {
                then("it should fail with the default limit") {
                    val exception = shouldThrow<YAMLException> {
                        loader(env = mapOf("YAML_MAX_ALIASES" to " ")).load(yaml)
                    }
                    exception.message shouldContain "max=50"
                }
            }

            `when`("the same loader parses multiple files with a raised limit") {
                then("all of them should be parsed") {
                    val loader = loader(env = mapOf("YAML_MAX_ALIASES" to "100"))

                    loader.load(yaml)["monitors.m50.uptime-check-interval"] shouldBe 180
                    loader.load(yaml)["monitors.m50.uptime-check-interval"] shouldBe 180
                }
            }
        }

        given("an invalid limit") {
            `when`("it's not a number in the environment variable") {
                then("it should fail with a ConfigurationException") {
                    val exception = shouldThrow<ConfigurationException> {
                        loader(env = mapOf("YAML_MAX_ALIASES" to "abc")).load("foo: bar")
                    }
                    exception.message shouldContain "YAML_MAX_ALIASES must be a positive integer, but it was: abc"
                }
            }

            `when`("it's not positive in the environment variable") {
                then("it should fail with a ConfigurationException") {
                    val exception = shouldThrow<ConfigurationException> {
                        loader(env = mapOf("YAML_MAX_ALIASES" to "0")).load("foo: bar")
                    }
                    exception.message shouldContain "YAML_MAX_ALIASES must be a positive integer, but it was: 0"
                }
            }
        }

        given("a YAML configuration file with a timestamp") {
            `when`("it's parsed with a raised limit") {
                then("the timestamp should be constructed the same way as by Micronaut's built-in loader") {
                    val properties = loader(env = mapOf("YAML_MAX_ALIASES" to "100"))
                        .load("start: 2026-10-13T10:00:00+02:00")

                    properties["start"].shouldBeInstanceOf<OffsetDateTime>()
                }
            }
        }

        given("a YAML configuration file that uses the features of Micronaut's built-in loader") {
            `when`("it's parsed by both loaders") {
                then("the result should be the same") {
                    val resource = "/yaml-config/micronaut-features.yml"
                    val content = requireNotNull(YamlConfigPropertySourceLoaderTest::class.java.getResource(resource))
                        .readBytes()

                    val expected = YamlPropertySourceLoader().read("kuvasz", content.inputStream())
                    val actual = loader(env = mapOf("YAML_MAX_ALIASES" to "100")).read("kuvasz", content.inputStream())

                    actual shouldBe expected
                    val monitors = actual["http-monitors"].shouldBeInstanceOf<List<Map<String, Any>>>()
                    monitors[0]["uptime-check-interval"] shouldBe 180
                    monitors[1]["uptimeCheckInterval"] shouldBe 60
                }
            }
        }

        given("the loader registered as a service") {
            `when`("a configuration file is passed via micronaut.config.files, with a raised limit in the env") {
                then("it should take over the parsing of the file from Micronaut's built-in YAML loader") {
                    System.setProperty(
                        Environment.PROPERTY_SOURCES_KEY,
                        "classpath:yaml-config/many-aliases.yml"
                    )
                    val environment = ApplicationContext.builder("test").build().environment
                    try {
                        environment.start()

                        environment.propertySourceLoaders shouldExist { it is YamlConfigPropertySourceLoader }
                        environment.getRequiredProperty(
                            "monitors.m59.uptime-check-interval",
                            Int::class.java,
                        ) shouldBe 180
                    } finally {
                        environment.stop()
                        System.clearProperty(Environment.PROPERTY_SOURCES_KEY)
                    }
                }
            }

            `when`("a configuration file with placeholders and camelCase keys is passed via micronaut.config.files") {
                then("they should be resolved the same way as by Micronaut's built-in YAML loader") {
                    System.setProperty(Environment.PROPERTY_SOURCES_KEY, "classpath:yaml-config/placeholders.yml")
                    System.setProperty("placeholder-test-system-property", "system-property-value")
                    System.setProperty("placeholder-test.overridden-by-system-property", "from-system-property")
                    val environment = ApplicationContext.builder("test").build().environment
                    try {
                        environment.start()

                        environment.property("placeholder-test.with-fallback") shouldBe "fallback-value"
                        environment.property("placeholder-test.from-env") shouldBe "100"
                        environment.property("placeholder-test.from-system-property") shouldBe "system-property-value"
                        environment.property("placeholder-test.camel-case-key") shouldBe "camel-value"
                        environment.property("placeholder-test.overridden-by-system-property") shouldBe
                            "from-system-property"
                        environment.property("yaml-max-aliases") shouldBe "100"
                    } finally {
                        environment.stop()
                        System.clearProperty(Environment.PROPERTY_SOURCES_KEY)
                        System.clearProperty("placeholder-test-system-property")
                        System.clearProperty("placeholder-test.overridden-by-system-property")
                    }
                }
            }

            `when`("the standard files (e.g. application.yml) are loaded") {
                then("it should leave them to Micronaut's built-in YAML loader, so they're parsed only once") {
                    val environment = ApplicationContext.builder("test").build().environment
                    try {
                        environment.start()

                        environment.propertySources.count { it.name == "application" } shouldBe 1
                        environment.propertySources.count { it.name == "application-test" } shouldBe 1
                    } finally {
                        environment.stop()
                    }
                }
            }
        }
    }
)

private const val DEFAULT_LIMIT_EXCEEDED = 51

private fun loader(env: Map<String, String> = emptyMap()) = YamlConfigPropertySourceLoader(getEnv = env::get)

private fun Environment.property(name: String) = getProperty(name, String::class.java).orElse(null)

private fun YamlConfigPropertySourceLoader.load(yaml: String) = read("kuvasz", yaml.byteInputStream())

private fun monitorsWithSharedDefaults(count: Int) = buildString {
    appendLine("defaults: &defaults")
    appendLine("  uptime-check-interval: 180")
    appendLine("monitors:")
    repeat(count) { appendLine("  m$it: { <<: *defaults }") }
}
