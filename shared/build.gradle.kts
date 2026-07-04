import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.i18n4k)

}

dependencies {
    implementation(libs.i18n4k)
    implementation(mn.slf4j.api)

    testImplementation(mn.kotest.runner.junit5.jvm)
    testImplementation(mn.kotest.assertions.core.jvm)
    testImplementation(libs.kotest.data)
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<Detekt>().configureEach {
    exclude("/com/kuvaszuptime/kuvasz/i18n")
}

tasks.register("validateI18n") {
    dependsOn("generateI18n4kFiles")
    group = "Verification"
    description = "Checks all i18n files against the English reference for missing keys and comments."

    // Define the root directory for your i18n files
    val i18nDir = file("src/main/i18n/com/kuvaszuptime/kuvasz/i18n")
    val referenceFile = file("$i18nDir/Messages_en.properties")

    doLast {
        if (!referenceFile.exists()) {
            throw RuntimeException("Reference i18n file not found: ${referenceFile.absolutePath}")
        }

        val parsePropertiesFile: (File) -> Map<Int, String> = { fileToParse ->
            val result = mutableMapOf<Int, String>()
            fileToParse.readLines().forEachIndexed { index, line ->
                val lineNumber = index + 1
                if (line.trim().startsWith("#") || line.trim().isEmpty()) {
                    // It's a comment or a blank line, store the full line content
                    result[lineNumber] = line
                } else if (!line.contains('=')) {
                    // Invalid line, just ignore it
                } else {
                    val key = line.substringBefore("=").trim()
                    result[lineNumber] = key
                }
            }
            result
        }

        val translationFilePattern = "Messages_([a-z]{2}).properties".toRegex()

        // Parse the reference English file
        val referenceMap = parsePropertiesFile(referenceFile)
        val referenceKeys = referenceMap.values.filter { it.isNotBlank() && !it.startsWith("#") }.toSet()

        // Find all other translation files and validate them
        i18nDir.listFiles()?.forEach { file ->
            if (file.name != referenceFile.name && file.name.matches(translationFilePattern)) {
                logger.lifecycle("--------------------------------------------------------")
                logger.lifecycle("Validating: ${file.name}")

                val translationMap = parsePropertiesFile(file)
                val translationKeys = translationMap.values.filter { it.isNotBlank() && !it.startsWith("#") }.toSet()

                // Check for missing keys
                val missingKeys = referenceKeys - translationKeys
                if (missingKeys.isNotEmpty()) {
                    logger.lifecycle("  ❌ Missing keys:")
                    missingKeys.forEach { key ->
                        logger.lifecycle("    - $key")
                    }
                }

                // Check for extra keys (not in English file)
                val extraKeys = translationKeys - referenceKeys
                if (extraKeys.isNotEmpty()) {
                    logger.lifecycle("  ⚠️ Extra keys found:")
                    extraKeys.forEach { key ->
                        logger.lifecycle("    - $key")
                    }
                }

                // Check for order and missing comments/keys
                var hasOrderIssues = false
                referenceMap.forEach { (lineNumber, referenceValue) ->
                    val translationValueAtLine = translationMap[lineNumber]

                    if (translationValueAtLine == null) {
                        logger.lifecycle("  ❌ Line $lineNumber is completely missing from ${file.name}: '$referenceValue'")
                        hasOrderIssues = true
                    } else if (translationValueAtLine != referenceValue) {
                        if (referenceValue.startsWith("#") || referenceValue.trim().isEmpty()) {
                            logger.lifecycle("  ⚠️ Mismatched content at line $lineNumber. Expected a comment/blank line but found: '$translationValueAtLine'")
                        } else {
                            logger.lifecycle("  ❌ Mismatched keys or order at line $lineNumber. Expected: '$referenceValue' Found: '$translationValueAtLine'")
                        }
                        hasOrderIssues = true
                    }
                }

                if (!hasOrderIssues && missingKeys.isEmpty() && extraKeys.isEmpty()) {
                    logger.lifecycle("  ✅ All keys and comments are in sync!")
                } else {
                    throw GradleException("Validation failed for ${file.name}. Please fix the issues above.")
                }
                logger.lifecycle("--------------------------------------------------------")
            }
        }
    }
}
