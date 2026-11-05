package com.cloud.launcher.data

enum class ClockFormat { H24, H12 }

enum class ClockStyle { DIGITAL, THIN, STACKED, ANALOG }

enum class GlassType { LIQUID, FROST, CLEAR, SOLID }

enum class IconShape { ORIGINAL, CIRCLE, SQUIRCLE, ROUNDED }

enum class DrawerLayout { GRID, LIST }

enum class DrawerBackground { THEME, BLACK, ACCENT }

data class LauncherSettings(
    val clockFormat: ClockFormat = ClockFormat.H24,
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val showAmPm: Boolean = true,
    val showDate: Boolean = true,
    val showStats: Boolean = true,
    val accentIndex: Int = 0,
    val backgroundIndex: Int = 0,
    val glassType: GlassType = GlassType.LIQUID,
    val glassTintIndex: Int = 0,
    val glassIntensity: Float = 0.5f,
    val drawerLayout: DrawerLayout = DrawerLayout.GRID,
    val favoritesCount: Int = 5,
    val drawerColumns: Int = 4,
    val drawerIconSize: Int = 54,
    val drawerShowLabels: Boolean = true,
    val iconShape: IconShape = IconShape.ORIGINAL,
    val drawerBackground: DrawerBackground = DrawerBackground.THEME,
    val doubleTapSleep: Boolean = true,
    val doubleTapWake: Boolean = true,
    val backKeepProtected: Boolean = true,
    val autoMemory: Boolean = true
)
