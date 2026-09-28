package com.kuvaszuptime.kuvasz.ui

import com.kuvaszuptime.kuvasz.i18n.Messages

internal interface ThemeOption {
    val value: String
    val label: String
}

internal enum class ThemeBase : ThemeOption {
    GRAY,
    SLATE,
    ZINC,
    NEUTRAL,
    STONE,
    PINK;

    override val value: String get() = name.lowercase()

    override val label: String
        get() = when (this) {
            GRAY -> Messages.themeColorGray()
            SLATE -> Messages.themeColorSlate()
            ZINC -> Messages.themeColorZinc()
            NEUTRAL -> Messages.themeColorNeutral()
            STONE -> Messages.themeColorStone()
            PINK -> Messages.themeColorPink()
        }

    companion object {
        // Tabler switched to the neutral palette in 1.6, but the blue-tinted one it used before is kept as the default
        val DEFAULT = GRAY
    }
}

internal enum class ThemePrimary : ThemeOption {
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

    override val value: String get() = name.lowercase()

    override val label: String
        get() = when (this) {
            BLUE -> Messages.themeColorBlue()
            AZURE -> Messages.themeColorAzure()
            INDIGO -> Messages.themeColorIndigo()
            PURPLE -> Messages.themeColorPurple()
            PINK -> Messages.themeColorPink()
            RED -> Messages.themeColorRed()
            ORANGE -> Messages.themeColorOrange()
            YELLOW -> Messages.themeColorYellow()
            LIME -> Messages.themeColorLime()
            GREEN -> Messages.themeColorGreen()
            TEAL -> Messages.themeColorTeal()
            CYAN -> Messages.themeColorCyan()
            INVERTED -> Messages.themeColorInverted()
        }

    companion object {
        val DEFAULT = BLUE
    }
}
