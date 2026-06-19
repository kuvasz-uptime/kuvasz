import dev.detekt.gradle.Detekt
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import nl.littlerobots.vcu.plugin.resolver.VersionSelectors
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.git.version)
    alias(libs.plugins.version.catalog.update)
}

val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()
val javaTargetVersion = "25"

/**
 * This setup (together with the "kover()" dependency configurations below) will create a merged test coverage report
 */
kover {
    reports {
        total {
            verify {
                onCheck = true
                rule {
                    bound {
                        minValue = 90
                        coverageUnits = CoverageUnit.INSTRUCTION
                    }
                }
            }
            filters {
                excludes {
                    classes("*ApplicationKt*", "*MacOsPingExecutor*")
                    packages("com.kuvaszuptime.kuvasz.jooq")
                }
            }
            html { onCheck = false }
        }
    }
}

dependencies {
    kover(project(":app"))
    kover(project(":model"))
    kover(project(":shared"))
}

/**
 * Returns the module name honoring java package name conventions. For example: kuvasz.app, kuvasz.model.
 * Required by tasks like `detektAll`.
 *
 * Copied over from `name.remal.check-updates` plugin (it got abandoned)
 */
val Project.javaModuleName: String
    get() {
        val projectId = "${this.group}.${this.name}"
            .splitToSequence('.', ':')
            .filter(CharSequence::isNotEmpty)
            .joinToString(".")

        val javaPackageNameProhibitedChars = Regex("[^.\\w]") // special chars
        val javaPackageNameProhibitedCharsAfterDot = Regex("\\.([^A-Za-z_])") // number after dot

        return projectId
            .replace(javaPackageNameProhibitedChars, "_")
            .replace(javaPackageNameProhibitedCharsAfterDot, "._$1")
    }

/**
 *  Groups together all the known Detekt tasks
 */
tasks.register("detektAll", type = Detekt::class) {
    val includedTasks = setOf("detekt", "detektMain", "detektTest", "detektUiTest")
    subprojects.forEach { project ->
        project.getAllTasks(true).values.flatten().forEach { task ->
            if (includedTasks.contains(task.name)) {
                val projectName = task.project.javaModuleName.removePrefix("kuvasz.").replace(".", ":")
                dependsOn("$projectName:${task.name}")
            }
        }
    }
}

versionCatalogUpdate {
    versionSelector(VersionSelectors.STABLE)
}

subprojects {
    version = rootProject.version

    // Ensure that the same Java target version is used in all kotlin projects
    extensions.findByType<KotlinJvmProjectExtension>()?.apply {
        jvmToolchain {
            this.languageVersion.set(JavaLanguageVersion.of(javaTargetVersion))
        }
    }
}
