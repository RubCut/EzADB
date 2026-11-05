package dev.ezadb.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.ezadb.core.ConnectionKind
import dev.ezadb.core.Endpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Accent(val seed: Long) {
    GREEN(0xFF006E1C),
    TEAL(0xFF00696D),
    BLUE(0xFF0061A4),
    PURPLE(0xFF6750A4),
    ORANGE(0xFF8B5000),
}

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    RUSSIAN("ru"),
    UKRAINIAN("uk"),
    GERMAN("de"),
    SPANISH("es"),
    FRENCH("fr"),
    CHINESE("zh-CN"),
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: Accent = Accent.GREEN,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val introSeen: Boolean = false,
    val terminalFontSize: Int = 13,
    val confirmDestructive: Boolean = true,
    val demoMode: Boolean = false,
    val keepScreenOn: Boolean = true,
    val haptics: Boolean = true,
    val recentEndpoints: List<Endpoint> = emptyList(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ezadb_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val accent = stringPreferencesKey("accent")
        val language = stringPreferencesKey("app_language")
        val introSeen = booleanPreferencesKey("intro_seen")
        val fontSize = intPreferencesKey("terminal_font_size")
        val confirm = booleanPreferencesKey("confirm_destructive")
        val demo = booleanPreferencesKey("demo_mode")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val haptics = booleanPreferencesKey("haptics")
        val recent = stringSetPreferencesKey("recent_endpoints")
    }

    val flow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            themeMode = prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.dynamic] ?: true,
            accent = prefs[Keys.accent]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.GREEN,
            language = prefs[Keys.language]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM,
            introSeen = prefs[Keys.introSeen] ?: false,
            terminalFontSize = prefs[Keys.fontSize] ?: 13,
            confirmDestructive = prefs[Keys.confirm] ?: true,
            demoMode = prefs[Keys.demo] ?: false,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            haptics = prefs[Keys.haptics] ?: true,
            recentEndpoints = prefs[Keys.recent]?.mapNotNull(::decodeEndpoint)?.sortedByDescending { it.label } ?: emptyList(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamic] = value }
    suspend fun setAccent(accent: Accent) = edit { it[Keys.accent] = accent.name }
    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.language] = language.name }
    suspend fun setIntroSeen(value: Boolean) = edit { it[Keys.introSeen] = value }
    suspend fun setTerminalFontSize(size: Int) = edit { it[Keys.fontSize] = size.coerceIn(10, 22) }
    suspend fun setConfirmDestructive(value: Boolean) = edit { it[Keys.confirm] = value }
    suspend fun setDemoMode(value: Boolean) = edit { it[Keys.demo] = value }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepScreenOn] = value }
    suspend fun setHaptics(value: Boolean) = edit { it[Keys.haptics] = value }

    suspend fun addRecent(endpoint: Endpoint) = edit { prefs ->
        val current = prefs[Keys.recent]?.mapNotNull(::decodeEndpoint)?.toMutableList() ?: mutableListOf()
        current.removeAll { it.host == endpoint.host && it.port == endpoint.port }
        current.add(0, endpoint)
        prefs[Keys.recent] = current.take(8).map(::encodeEndpoint).toSet()
    }

    suspend fun removeRecent(endpoint: Endpoint) = edit { prefs ->
        val current = prefs[Keys.recent]?.mapNotNull(::decodeEndpoint)?.toMutableList() ?: mutableListOf()
        current.removeAll { it.host == endpoint.host && it.port == endpoint.port }
        prefs[Keys.recent] = current.map(::encodeEndpoint).toSet()
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun encodeEndpoint(endpoint: Endpoint): String =
        "${endpoint.host}|${endpoint.port}|${endpoint.kind.name}|${endpoint.label.replace('|', '/')}"

    private fun decodeEndpoint(raw: String): Endpoint? {
        val parts = raw.split('|')
        if (parts.size < 3) return null
        val port = parts[1].toIntOrNull() ?: return null
        val kind = runCatching { ConnectionKind.valueOf(parts[2]) }.getOrNull() ?: ConnectionKind.TCP
        val label = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "${parts[0]}:$port"
        return Endpoint(parts[0], port, label, kind)
    }
}
