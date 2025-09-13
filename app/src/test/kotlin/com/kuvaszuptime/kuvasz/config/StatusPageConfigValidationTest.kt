package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.dto.StatusPageValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageConfigDefaults
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.util.StringUtils
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * These tests are meant to check if a wrongly configured StatusPageConfig in the YAML files
 * really hinders the application from starting as expected.
 */
@MicronautTest(startApplication = false)
class StatusPageConfigValidationTest : DatabaseBehaviorSpec({

    given("a StatusPageConfig bean") {

        `when`("slug is a blank string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("sp-blank-slug")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "StatusPageConfig.getSlug - ${StatusPageValidationMessages.SLUG_NOT_BLANK}"
            }
        }

        `when`("slug has invalid characters") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("sp-invalid-slug")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "StatusPageConfig.getSlug - ${StatusPageValidationMessages.SLUG_PATTERN}"
            }
        }

        `when`("title is a blank string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("sp-blank-title")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "StatusPageConfig.getTitle - ${StatusPageValidationMessages.TITLE_NOT_BLANK}"
            }
        }

        `when`("monitors contain an invalid monitor ID") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("sp-missing-monitor")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "Non-existing monitor ID found: http:test2. " +
                    "Make sure the monitor is defined before referencing it."
            }
        }
    }
})

/**
 * These tests are meant to check if a StatusPageConfig and StatusPagesConfig  bean with default values
 * is created correctly when not all the properties are explicitly set in the YAML.
 *
 * It extends a DatabaseBehaviorSpec just to delete the inserted status pages properly to not affect other tests
 */
@MicronautTest(startApplication = false, environments = ["sp-without-defaults"])
class StatusPageConfigDefaultValuesTest(applicationContext: ApplicationContext) : DatabaseBehaviorSpec({

    given("the StatusPage config beans") {

        `when`("not all the properties are explicitly set in the YAML") {

            then("it should fall back to the right default values") {
                val statusPageConfig = applicationContext.getBean<StatusPageConfig>()
                statusPageConfig.enabled shouldBe StatusPageConfigDefaults.CUSTOM_PAGE_ENABLED
                statusPageConfig.title shouldBe StatusPageConfigDefaults.TITLE
                statusPageConfig.slug shouldBe "valid_slug"
                statusPageConfig.monitors.shouldBeNull()

                val statusPageDefaultConfig = applicationContext.getBean<DefaultStatusPageConfig>()
                statusPageDefaultConfig.enabled shouldBe StatusPageConfigDefaults.DEFAULT_PAGE_ENABLED
                statusPageDefaultConfig.title shouldBe StatusPageConfigDefaults.TITLE
            }
        }
    }
})

/**
 * These tests are meant to check if a StatusPagesConfig is overriding the default values correctly.
 */
@MicronautTest(startApplication = false)
@Property(name = "default-status-page.enabled", value = StringUtils.TRUE)
@Property(name = "default-status-page.title", value = "Something custom")
class DefaultStatusPageConfigTest(applicationContext: ApplicationContext) : BehaviorSpec({

    given("the DefaultStatusPagesConfig bean") {

        `when`("properties are explicitly set in the YAML") {

            then("it should use them over the default values") {
                val statusPageDefaultConfig = applicationContext.getBean<DefaultStatusPageConfig>()
                statusPageDefaultConfig.enabled shouldBe true
                statusPageDefaultConfig.title shouldBe "Something custom"
            }
        }
    }
})
