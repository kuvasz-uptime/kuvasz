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
        // Tabler switched to the neutral palette in 1.6, but the blue-tinted one it used before is kept as the default
        val DEFAULT = GRAY
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
