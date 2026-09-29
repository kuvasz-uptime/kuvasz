package com.kuvaszuptime.kuvasz.ui

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.theme.ThemeBase
import com.kuvaszuptime.kuvasz.models.theme.ThemeOption
import com.kuvaszuptime.kuvasz.models.theme.ThemePrimary

internal val ThemeOption.label: String
    get() = when (this) {
        ThemeBase.GRAY -> Messages.themeColorGray()
        ThemeBase.SLATE -> Messages.themeColorSlate()
        ThemeBase.ZINC -> Messages.themeColorZinc()
        ThemeBase.NEUTRAL -> Messages.themeColorNeutral()
        ThemeBase.STONE -> Messages.themeColorStone()
        ThemeBase.VIOLET -> Messages.themeColorViolet()
        ThemeBase.MIDNIGHT -> Messages.themeColorMidnight()
        ThemeBase.FROST -> Messages.themeColorFrost()
        ThemeBase.OCEAN -> Messages.themeColorOcean()
        ThemeBase.SAGE -> Messages.themeColorSage()
        ThemeBase.MOCHA -> Messages.themeColorMocha()
        ThemeBase.ROSE -> Messages.themeColorRose()
        ThemePrimary.BLUE -> Messages.themeColorBlue()
        ThemePrimary.AZURE -> Messages.themeColorAzure()
        ThemePrimary.INDIGO -> Messages.themeColorIndigo()
        ThemePrimary.PURPLE -> Messages.themeColorPurple()
        ThemePrimary.PINK -> Messages.themeColorPink()
        ThemePrimary.RED -> Messages.themeColorRed()
        ThemePrimary.ORANGE -> Messages.themeColorOrange()
        ThemePrimary.YELLOW -> Messages.themeColorYellow()
        ThemePrimary.LIME -> Messages.themeColorLime()
        ThemePrimary.GREEN -> Messages.themeColorGreen()
        ThemePrimary.TEAL -> Messages.themeColorTeal()
        ThemePrimary.CYAN -> Messages.themeColorCyan()
        ThemePrimary.INVERTED -> Messages.themeColorInverted()
    }
