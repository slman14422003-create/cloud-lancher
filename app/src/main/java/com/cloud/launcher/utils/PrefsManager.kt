package com.cloud.launcher.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import com.cloud.launcher.data.ClockFormat
import com.cloud.launcher.data.ClockStyle
import com.cloud.launcher.data.DrawerBackground
import com.cloud.launcher.data.DrawerLayout
import com.cloud.launcher.data.GlassType
import com.cloud.launcher.data.IconShape
import com.cloud.launcher.data.LauncherSettings

private val Context.cloudDataStore: DataStore<Preferences> by preferencesDataStore(name = "cloud_launcher_prefs")

private fun String?.toPkgList(): List<String> =
    if (this.isNullOrEmpty()) emptyList() else this.split(",").filter { it.isNotBlank() }

private fun String?.toLongMap(): Map<String, Long> {
    if (this.isNullOrEmpty()) return emptyMap()
    val result = LinkedHashMap<String, Long>()
    for (entry in this.split(",")) {
        val parts = entry.split(":")
        if (parts.size == 2) {
            val value = parts[1].toLongOrNull()
            if (value != null) result[parts[0]] = value
        }
    }
    return result
}

private fun Map<String, Long>.encode(): String =
    this.entries.joinToString(",") { it.key + ":" + it.value }

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    if (this == null) default else enumValues<E>().firstOrNull { it.name == this } ?: default

private object SettingsKeys {
    val CLOCK_FORMAT = stringPreferencesKey("set_clock_format")
    val CLOCK_STYLE = stringPreferencesKey("set_clock_style")
    val SHOW_AMPM = booleanPreferencesKey("set_show_ampm")
    val SHOW_DATE = booleanPreferencesKey("set_show_date")
    val SHOW_STATS = booleanPreferencesKey("set_show_stats")
    val ACCENT = intPreferencesKey("set_accent")
    val BACKGROUND = intPreferencesKey("set_background")
    val GLASS_TYPE = stringPreferencesKey("set_glass_type")
    val GLASS_TINT = intPreferencesKey("set_glass_tint")
    val GLASS_INTENSITY = floatPreferencesKey("set_glass_intensity")
    val DRAWER_LAYOUT = stringPreferencesKey("set_drawer_layout")
    val FAVORITES_COUNT = intPreferencesKey("set_favorites_count")
    val DRAWER_COLUMNS = intPreferencesKey("set_drawer_columns")
    val DRAWER_ICON_SIZE = intPreferencesKey("set_drawer_icon_size")
    val DRAWER_LABELS = booleanPreferencesKey("set_drawer_labels")
    val ICON_SHAPE = stringPreferencesKey("set_icon_shape")
    val DRAWER_BG = stringPreferencesKey("set_drawer_bg")
    val DOUBLE_TAP_SLEEP = booleanPreferencesKey("set_dt_sleep")
    val DOUBLE_TAP_WAKE = booleanPreferencesKey("set_dt_wake")
    val BACK_KEEP = booleanPreferencesKey("set_back_keep")
    val AUTO_MEMORY = booleanPreferencesKey("set_auto_memory")
}

private fun Preferences.toSettings(): LauncherSettings {
    val d = LauncherSettings()
    return LauncherSettings(
        clockFormat = this[SettingsKeys.CLOCK_FORMAT].toEnum(d.clockFormat),
        clockStyle = this[SettingsKeys.CLOCK_STYLE].toEnum(d.clockStyle),
        showAmPm = this[SettingsKeys.SHOW_AMPM] ?: d.showAmPm,
        showDate = this[SettingsKeys.SHOW_DATE] ?: d.showDate,
        showStats = this[SettingsKeys.SHOW_STATS] ?: d.showStats,
        accentIndex = this[SettingsKeys.ACCENT] ?: d.accentIndex,
        backgroundIndex = this[SettingsKeys.BACKGROUND] ?: d.backgroundIndex,
        glassType = this[SettingsKeys.GLASS_TYPE].toEnum(d.glassType),
        glassTintIndex = this[SettingsKeys.GLASS_TINT] ?: d.glassTintIndex,
        glassIntensity = this[SettingsKeys.GLASS_INTENSITY] ?: d.glassIntensity,
        drawerLayout = this[SettingsKeys.DRAWER_LAYOUT].toEnum(d.drawerLayout),
        favoritesCount = this[SettingsKeys.FAVORITES_COUNT] ?: d.favoritesCount,
        drawerColumns = this[SettingsKeys.DRAWER_COLUMNS] ?: d.drawerColumns,
        drawerIconSize = this[SettingsKeys.DRAWER_ICON_SIZE] ?: d.drawerIconSize,
        drawerShowLabels = this[SettingsKeys.DRAWER_LABELS] ?: d.drawerShowLabels,
        iconShape = this[SettingsKeys.ICON_SHAPE].toEnum(d.iconShape),
        drawerBackground = this[SettingsKeys.DRAWER_BG].toEnum(d.drawerBackground),
        doubleTapSleep = this[SettingsKeys.DOUBLE_TAP_SLEEP] ?: d.doubleTapSleep,
        doubleTapWake = this[SettingsKeys.DOUBLE_TAP_WAKE] ?: d.doubleTapWake,
        backKeepProtected = this[SettingsKeys.BACK_KEEP] ?: d.backKeepProtected,
        autoMemory = this[SettingsKeys.AUTO_MEMORY] ?: d.autoMemory
    )
}

private fun MutablePreferences.putSettings(s: LauncherSettings) {
    this[SettingsKeys.CLOCK_FORMAT] = s.clockFormat.name
    this[SettingsKeys.CLOCK_STYLE] = s.clockStyle.name
    this[SettingsKeys.SHOW_AMPM] = s.showAmPm
    this[SettingsKeys.SHOW_DATE] = s.showDate
    this[SettingsKeys.SHOW_STATS] = s.showStats
    this[SettingsKeys.ACCENT] = s.accentIndex
    this[SettingsKeys.BACKGROUND] = s.backgroundIndex
    this[SettingsKeys.GLASS_TYPE] = s.glassType.name
    this[SettingsKeys.GLASS_TINT] = s.glassTintIndex
    this[SettingsKeys.GLASS_INTENSITY] = s.glassIntensity
    this[SettingsKeys.DRAWER_LAYOUT] = s.drawerLayout.name
    this[SettingsKeys.FAVORITES_COUNT] = s.favoritesCount
    this[SettingsKeys.DRAWER_COLUMNS] = s.drawerColumns
    this[SettingsKeys.DRAWER_ICON_SIZE] = s.drawerIconSize
    this[SettingsKeys.DRAWER_LABELS] = s.drawerShowLabels
    this[SettingsKeys.ICON_SHAPE] = s.iconShape.name
    this[SettingsKeys.DRAWER_BG] = s.drawerBackground.name
    this[SettingsKeys.DOUBLE_TAP_SLEEP] = s.doubleTapSleep
    this[SettingsKeys.DOUBLE_TAP_WAKE] = s.doubleTapWake
    this[SettingsKeys.BACK_KEEP] = s.backKeepProtected
    this[SettingsKeys.AUTO_MEMORY] = s.autoMemory
}

class PrefsManager(context: Context) {

    private val store: DataStore<Preferences> = context.applicationContext.cloudDataStore

    private object Keys {
        val PROTECTED = stringPreferencesKey("protected_apps")
        val FAVORITES = stringPreferencesKey("favorite_apps")
        val HOME_APPS = stringPreferencesKey("home_apps")
        val RECENTS = stringPreferencesKey("recent_apps")
        val LAUNCH_COUNTS = stringPreferencesKey("launch_counts")
        val DISMISSED = stringPreferencesKey("dismissed_recents")
        val SORT_BY_USAGE = booleanPreferencesKey("sort_by_usage")
        val SETUP_SHOWN = booleanPreferencesKey("setup_shown")
        val COMPILED_VERSION = intPreferencesKey("compiled_version")
        val HOME_HINT_SHOWN = booleanPreferencesKey("home_hint_shown")
        val HIDDEN = stringPreferencesKey("hidden_apps")
    }

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val protectedApps: Flow<List<String>> = data.map { it[Keys.PROTECTED].toPkgList() }
    val favoriteApps: Flow<List<String>> = data.map { it[Keys.FAVORITES].toPkgList() }
    val homeApps: Flow<List<String>> = data.map { it[Keys.HOME_APPS].toPkgList() }
    val recentApps: Flow<List<String>> = data.map { it[Keys.RECENTS].toPkgList() }
    val launchCounts: Flow<Map<String, Long>> = data.map { it[Keys.LAUNCH_COUNTS].toLongMap() }
    val dismissedRecents: Flow<Map<String, Long>> = data.map { it[Keys.DISMISSED].toLongMap() }
    val sortByUsage: Flow<Boolean> = data.map { it[Keys.SORT_BY_USAGE] ?: false }
    val setupShown: Flow<Boolean> = data.map { it[Keys.SETUP_SHOWN] ?: false }
    val compiledVersion: Flow<Int> = data.map { it[Keys.COMPILED_VERSION] ?: 0 }
    val homeHintShown: Flow<Boolean> = data.map { it[Keys.HOME_HINT_SHOWN] ?: false }

    val hiddenApps: Flow<List<String>> = data.map { it[Keys.HIDDEN].toPkgList() }

    suspend fun setHidden(list: List<String>) {
        store.edit { it[Keys.HIDDEN] = list.joinToString(",") }
    }

    val settings: Flow<LauncherSettings> = data.map { it.toSettings() }

    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        store.edit { prefs -> prefs.putSettings(transform(prefs.toSettings())) }
    }

    suspend fun setProtected(list: List<String>) {
        store.edit { it[Keys.PROTECTED] = list.joinToString(",") }
    }

    suspend fun setFavorites(list: List<String>) {
        store.edit { it[Keys.FAVORITES] = list.joinToString(",") }
    }

    suspend fun setHomeApps(list: List<String>) {
        store.edit { it[Keys.HOME_APPS] = list.joinToString(",") }
    }

    suspend fun setRecents(list: List<String>) {
        store.edit { it[Keys.RECENTS] = list.joinToString(",") }
    }

    suspend fun setDismissed(map: Map<String, Long>) {
        store.edit { it[Keys.DISMISSED] = map.encode() }
    }

    suspend fun setSortByUsage(value: Boolean) {
        store.edit { it[Keys.SORT_BY_USAGE] = value }
    }

    suspend fun setSetupShown(value: Boolean) {
        store.edit { it[Keys.SETUP_SHOWN] = value }
    }

    suspend fun setCompiledVersion(value: Int) {
        store.edit { it[Keys.COMPILED_VERSION] = value }
    }

    suspend fun setHomeHintShown(value: Boolean) {
        store.edit { it[Keys.HOME_HINT_SHOWN] = value }
    }

    suspend fun incrementLaunch(pkg: String) {
        store.edit { prefs ->
            val map = prefs[Keys.LAUNCH_COUNTS].toLongMap().toMutableMap()
            map[pkg] = (map[pkg] ?: 0L) + 1L
            prefs[Keys.LAUNCH_COUNTS] = map.encode()
        }
    }
}
