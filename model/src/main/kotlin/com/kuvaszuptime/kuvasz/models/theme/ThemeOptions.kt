package com.kuvaszuptime.kuvasz.models.theme

sealed interface ThemeOption {
    val name: String

    val value: String get() = name.lowercase()
}

enum class ThemeBase : ThemeOption {
    GRAY,
    SLATE,
    ZINC,
    NEUTRAL,
    STONE;

    companion object {
        // Tabler switched to the neutral palette in 1.6, but the blue-tinted one it used before is kept as the default.
        // Its name is a constant too, for the annotations of the configuration
        const val DEFAULT_NAME = "GRAY"
        val DEFAULT = valueOf(DEFAULT_NAME)
    }
}

enum class ThemePrimary : ThemeOption {
    BLUE,
    AZURE,
    INDIGO,
    PURPLE,
    PINK,
    RED,
    ORANGE,
    YELLOW,
    LIME,
    GREEN,
    TEAL,
    CYAN,
    INVERTED;

    companion object {
        val DEFAULT = BLUE
    }
}
