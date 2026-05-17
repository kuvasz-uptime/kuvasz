import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import dev.detekt.gradle.Detekt
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

buildscript {
    val jooqVersion: String by project
    configurations["classpath"].resolutionStrategy.eachDependency {
        if (requested.group == "org.jooq") {
            useVersion(jooqVersion)
        }
    }
}

plugins {
    kotlin("jvm") apply false
    id("dev.detekt") apply false
    id("org.jetbrains.kotlinx.kover")
    id("com.palantir.git-version")
    id("com.github.ben-manes.versions") apply false
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
    val includedTasks = setOf("detekt", "detektMain", "detektTest")
    subprojects.forEach { project ->
        project.getAllTasks(true).values.flatten().forEach { task ->
            if (includedTasks.contains(task.name)) {
                val projectName = task.project.javaModuleName.removePrefix("kuvasz.").replace(".", ":")
                dependsOn("$projectName:${task.name}")
            }
        }
    }
}

subprojects {
    version = rootProject.version

    // Ensure that the same Java target version is used in all kotlin projects
    extensions.findByType<KotlinJvmProjectExtension>()?.apply {
        jvmToolchain {
            this.languageVersion.set(JavaLanguageVersion.of(javaTargetVersion))
        }
    }

    tasks.withType<DependencyUpdatesTask> {
        rejectVersionIf {
            isNonStable(candidate.version)
        }
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}
