package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AlphabetMode
import com.example.model.AppAdaptationMode
import com.example.model.AppIconMode
import com.example.model.AppIconOverride
import com.example.model.AppLanguage
import com.example.model.HubQuickLink
import com.example.model.HubSubTab
import com.example.model.PlayerCoverStyle
import com.example.model.UiThemeColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private val Context.launcherDataStore by preferencesDataStore(name = "keydeck_launcher_prefs")

class LauncherPreferencesRepository(private val context: Context) {

    private object Keys {
        val PINNED_APPS = stringSetPreferencesKey("pinned_apps")
        val ALPHABET_MODE = stringPreferencesKey("alphabet_mode")
        val PREFERRED_MEDIA_PKG = stringPreferencesKey("preferred_media_pkg")
        val HUB_LINKS = stringSetPreferencesKey("hub_quick_links")
        val INTERCEPT_SHADE = booleanPreferencesKey("shizuku_intercept_shade")
        val INTERCEPT_LOCKSCREEN = booleanPreferencesKey("shizuku_intercept_lockscreen")
        val SEARCH_TREE_URIS = stringSetPreferencesKey("universal_search_tree_uris")
        val CUSTOM_APP_LABELS = stringSetPreferencesKey("custom_app_labels")
        val APP_ICON_OVERRIDES = stringSetPreferencesKey("app_icon_overrides")
        val UI_THEME_COLOR = stringPreferencesKey("ui_theme_color")
        val WALLPAPER_PATH = stringPreferencesKey("wallpaper_path")
        val AUTO_KILL_ENABLED = booleanPreferencesKey("auto_kill_enabled")
        val AUTO_KILL_PACKAGES = stringSetPreferencesKey("auto_kill_packages")
        val AUTO_KILL_INITIALIZED = booleanPreferencesKey("auto_kill_initialized")
        val BG_SPOOF_ENABLED = booleanPreferencesKey("bg_spoof_enabled")
        val BG_SPOOF_PACKAGES = stringSetPreferencesKey("bg_spoof_packages")
        val BG_SPOOF_INITIALIZED = booleanPreferencesKey("bg_spoof_initialized")
        val ADAPTATION_ENABLED = booleanPreferencesKey("app_adaptation_enabled")
        val ADAPTATION_MODES = stringSetPreferencesKey("app_adaptation_modes")
        val ADAPTATION_INITIALIZED = booleanPreferencesKey("app_adaptation_initialized")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val UI_SCALE = floatPreferencesKey("ui_scale")
        val PLAYER_COVER_STYLE = stringPreferencesKey("player_cover_style")
        val PINNED_RECENTS = stringSetPreferencesKey("pinned_recents")
        val OEM_GESTURE_BYPASS = booleanPreferencesKey("oem_gesture_bypass")
    }

    val pinnedAppIds: Flow<Set<String>> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.PINNED_APPS] ?: emptySet() }
        .onStart { emit(emptySet()) }

    val alphabetMode: Flow<AlphabetMode> = context.launcherDataStore.data
        .map { prefs ->
            when (prefs[Keys.ALPHABET_MODE]) {
                AlphabetMode.CYRILLIC.name -> AlphabetMode.CYRILLIC
                else -> AlphabetMode.LATIN
            }
        }
        .onStart { emit(AlphabetMode.LATIN) }

    val preferredMediaPackage: Flow<String?> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.PREFERRED_MEDIA_PKG] }
        .onStart { emit(null) }

    val interceptShadeEnabled: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.INTERCEPT_SHADE] ?: false }
        .onStart { emit(false) }

    val interceptLockScreenEnabled: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.INTERCEPT_LOCKSCREEN] ?: false }
        .onStart { emit(false) }

    val searchTreeUris: Flow<Set<String>> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.SEARCH_TREE_URIS] ?: emptySet() }
        .onStart { emit(emptySet()) }

    val customAppLabels: Flow<Map<String, String>> = context.launcherDataStore.data
        .map { prefs ->
            val raw = prefs[Keys.CUSTOM_APP_LABELS] ?: emptySet()
            buildMap {
                raw.forEach { entry ->
                    val sep = entry.indexOf("||")
                    if (sep > 0) {
                        val id = entry.substring(0, sep)
                        val label = entry.substring(sep + 2).trim()
                        if (label.isNotEmpty()) put(id, label)
                    }
                }
            }
        }
        .onStart { emit(emptyMap()) }

    val appIconOverrides: Flow<Map<String, AppIconOverride>> = context.launcherDataStore.data
        .map { prefs ->
            val raw = prefs[Keys.APP_ICON_OVERRIDES] ?: emptySet()
            buildMap {
                raw.forEach { entry ->
                    val parts = entry.split("||", limit = 4)
                    if (parts.size >= 2) {
                        val id = parts[0]
                        val mode = runCatching { AppIconMode.valueOf(parts[1]) }.getOrDefault(AppIconMode.AUTO)
                        val symKey = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                        val customPath = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                        if (mode != AppIconMode.AUTO) {
                            put(id, AppIconOverride(mode = mode, symbianKey = symKey, customFilePath = customPath))
                        }
                    }
                }
            }
        }
        .onStart { emit(emptyMap()) }

    val hubQuickLinks: Flow<List<HubQuickLink>> = context.launcherDataStore.data
        .map { prefs ->
            val raw = prefs[Keys.HUB_LINKS] ?: emptySet()
            raw.mapNotNull { entry ->
                val parts = entry.split("||")
                if (parts.size >= 4) {
                    val category = runCatching { HubSubTab.valueOf(parts[3]) }.getOrDefault(HubSubTab.NEWS)
                    HubQuickLink(id = parts[0], title = parts[1], url = parts[2], category = category)
                } else {
                    null
                }
            }.sortedBy { it.title.lowercase() }
        }
        .onStart { emit(emptyList()) }

    val uiThemeColor: Flow<UiThemeColor> = context.launcherDataStore.data
        .map { prefs ->
            when (prefs[Keys.UI_THEME_COLOR]) {
                UiThemeColor.GRAY.name -> UiThemeColor.GRAY
                else -> UiThemeColor.BLUE
            }
        }
        .onStart { emit(UiThemeColor.BLUE) }

    val wallpaperPath: Flow<String?> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.WALLPAPER_PATH]?.takeIf { it.isNotBlank() } }
        .onStart { emit(null) }

    val autoKillEnabled: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.AUTO_KILL_ENABLED] ?: true }
        .onStart { emit(true) }

    val autoKillPackages: Flow<Set<String>> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.AUTO_KILL_PACKAGES] ?: emptySet() }
        .onStart { emit(emptySet()) }

    val backgroundSpoofEnabled: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.BG_SPOOF_ENABLED] ?: false }
        .onStart { emit(false) }

    val backgroundSpoofPackages: Flow<Set<String>> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.BG_SPOOF_PACKAGES] ?: emptySet() }
        .onStart { emit(emptySet()) }

    val appAdaptationEnabled: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.ADAPTATION_ENABLED] ?: false }
        .onStart { emit(false) }

    val appAdaptationModes: Flow<Map<String, AppAdaptationMode>> = context.launcherDataStore.data
        .map { prefs ->
            val raw = prefs[Keys.ADAPTATION_MODES] ?: emptySet()
            buildMap {
                raw.forEach { entry ->
                    val sep = entry.indexOf("||")
                    if (sep > 0) {
                        val pkg = entry.substring(0, sep)
                        val mode = runCatching {
                            AppAdaptationMode.valueOf(entry.substring(sep + 2))
                        }.getOrDefault(AppAdaptationMode.DISABLED)
                        if (mode != AppAdaptationMode.DISABLED) {
                            put(pkg, mode)
                        }
                    }
                }
            }
        }
        .onStart { emit(emptyMap()) }

    val appLanguage: Flow<AppLanguage> = context.launcherDataStore.data
        .map { prefs ->
            when (prefs[Keys.APP_LANGUAGE]) {
                AppLanguage.EN.name -> AppLanguage.EN
                else -> AppLanguage.RU
            }
        }
        .onStart { emit(AppLanguage.RU) }

    val uiScale: Flow<Float> = context.launcherDataStore.data
        .map { prefs -> (prefs[Keys.UI_SCALE] ?: 1.0f).coerceIn(0.80f, 1.30f) }
        .onStart { emit(1.0f) }

    val playerCoverStyle: Flow<PlayerCoverStyle> = context.launcherDataStore.data
        .map { prefs ->
            when (prefs[Keys.PLAYER_COVER_STYLE]) {
                PlayerCoverStyle.DISC.name -> PlayerCoverStyle.DISC
                else -> PlayerCoverStyle.COVER
            }
        }
        .onStart { emit(PlayerCoverStyle.COVER) }

    val pinnedRecentPackages: Flow<Set<String>> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.PINNED_RECENTS] ?: emptySet() }
        .onStart { emit(emptySet()) }

    val oemGestureBypassEnabled: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.OEM_GESTURE_BYPASS] ?: false }
        .onStart { emit(false) }

    suspend fun togglePinApp(appId: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_APPS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(appId)) {
                current.remove(appId)
            }
            prefs[Keys.PINNED_APPS] = current
        }
    }

    suspend fun setAlphabetMode(mode: AlphabetMode) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.ALPHABET_MODE] = mode.name
        }
    }

    suspend fun setPreferredMediaPackage(packageName: String) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_MEDIA_PKG] = packageName
        }
    }

    suspend fun setInterceptShadeEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.INTERCEPT_SHADE] = enabled
        }
    }

    suspend fun setInterceptLockScreenEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.INTERCEPT_LOCKSCREEN] = enabled
        }
    }

    suspend fun addSearchTreeUri(uriString: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.SEARCH_TREE_URIS]?.toMutableSet() ?: mutableSetOf()
            current.add(uriString)
            prefs[Keys.SEARCH_TREE_URIS] = current
        }
    }

    suspend fun removeSearchTreeUri(uriString: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.SEARCH_TREE_URIS]?.toMutableSet() ?: mutableSetOf()
            current.remove(uriString)
            prefs[Keys.SEARCH_TREE_URIS] = current
        }
    }

    suspend fun setCustomAppLabel(appId: String, customLabel: String?) {
        val cleaned = customLabel?.replace("||", " ")?.trim().orEmpty()
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_APP_LABELS]?.toMutableSet() ?: mutableSetOf()
            current.removeAll { it.startsWith("$appId||") }
            if (cleaned.isNotEmpty()) {
                current.add("$appId||$cleaned")
            }
            prefs[Keys.CUSTOM_APP_LABELS] = current
        }
    }

    suspend fun setAppIconOverride(appId: String, override: AppIconOverride?) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.APP_ICON_OVERRIDES]?.toMutableSet() ?: mutableSetOf()
            current.removeAll { it.startsWith("$appId||") }
            if (override != null && override.mode != AppIconMode.AUTO) {
                val safeKey = override.symbianKey.orEmpty().replace("||", "")
                val safePath = override.customFilePath.orEmpty().replace("||", "")
                current.add("$appId||${override.mode.name}||$safeKey||$safePath")
            }
            prefs[Keys.APP_ICON_OVERRIDES] = current
        }
    }

    suspend fun addHubQuickLink(title: String, url: String, category: HubSubTab) {
        val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
        val id = "${System.currentTimeMillis()}"
        val serialized = "$id||${title.replace("||", " ")}||${normalizedUrl.replace("||", "")}||${category.name}"
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.HUB_LINKS]?.toMutableSet() ?: mutableSetOf()
            current.add(serialized)
            prefs[Keys.HUB_LINKS] = current
        }
    }

    suspend fun removeHubQuickLink(id: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.HUB_LINKS]?.toMutableSet() ?: mutableSetOf()
            current.removeAll { it.startsWith("$id||") }
            prefs[Keys.HUB_LINKS] = current
        }
    }

    suspend fun setUiThemeColor(color: UiThemeColor) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.UI_THEME_COLOR] = color.name
        }
    }

    suspend fun setWallpaperPath(path: String?) {
        context.launcherDataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(Keys.WALLPAPER_PATH)
            } else {
                prefs[Keys.WALLPAPER_PATH] = path
            }
        }
    }

    suspend fun setAutoKillEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.AUTO_KILL_ENABLED] = enabled
        }
    }

    suspend fun initDefaultAutoKillPackagesIfNeeded(defaults: Set<String>) {
        context.launcherDataStore.edit { prefs ->
            if (prefs[Keys.AUTO_KILL_INITIALIZED] != true) {
                prefs[Keys.AUTO_KILL_INITIALIZED] = true
                if (defaults.isNotEmpty()) {
                    prefs[Keys.AUTO_KILL_PACKAGES] = defaults
                }
            }
        }
    }

    suspend fun toggleAutoKillPackage(packageName: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_KILL_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(packageName)) {
                current.remove(packageName)
            }
            prefs[Keys.AUTO_KILL_INITIALIZED] = true
            prefs[Keys.AUTO_KILL_PACKAGES] = current
        }
    }

    suspend fun addAutoKillPackage(packageName: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_KILL_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            current.add(packageName)
            prefs[Keys.AUTO_KILL_INITIALIZED] = true
            prefs[Keys.AUTO_KILL_PACKAGES] = current
        }
    }

    suspend fun removeAutoKillPackage(packageName: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_KILL_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            current.remove(packageName)
            prefs[Keys.AUTO_KILL_INITIALIZED] = true
            prefs[Keys.AUTO_KILL_PACKAGES] = current
        }
    }

    suspend fun setBackgroundSpoofEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.BG_SPOOF_ENABLED] = enabled
        }
    }

    suspend fun initDefaultBackgroundSpoofPackagesIfNeeded(defaults: Set<String>) {
        context.launcherDataStore.edit { prefs ->
            if (prefs[Keys.BG_SPOOF_INITIALIZED] != true) {
                prefs[Keys.BG_SPOOF_INITIALIZED] = true
                if (defaults.isNotEmpty()) {
                    prefs[Keys.BG_SPOOF_PACKAGES] = defaults
                }
            }
        }
    }

    suspend fun toggleBackgroundSpoofPackage(packageName: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.BG_SPOOF_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(packageName)) {
                current.remove(packageName)
            }
            prefs[Keys.BG_SPOOF_INITIALIZED] = true
            prefs[Keys.BG_SPOOF_PACKAGES] = current
        }
    }

    suspend fun setAppAdaptationEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.ADAPTATION_ENABLED] = enabled
        }
    }

    suspend fun initDefaultAdaptationModesIfNeeded(defaults: Map<String, AppAdaptationMode>) {
        context.launcherDataStore.edit { prefs ->
            if (prefs[Keys.ADAPTATION_INITIALIZED] != true) {
                prefs[Keys.ADAPTATION_INITIALIZED] = true
                if (defaults.isNotEmpty()) {
                    prefs[Keys.ADAPTATION_MODES] = defaults.entries
                        .filter { it.value != AppAdaptationMode.DISABLED }
                        .map { "${it.key}||${it.value.name}" }
                        .toSet()
                }
            }
        }
    }

    suspend fun setAppAdaptationMode(packageName: String, mode: AppAdaptationMode) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.ADAPTATION_MODES]?.toMutableSet() ?: mutableSetOf()
            current.removeAll { it.startsWith("$packageName||") }
            if (mode != AppAdaptationMode.DISABLED) {
                current.add("$packageName||${mode.name}")
            }
            prefs[Keys.ADAPTATION_INITIALIZED] = true
            prefs[Keys.ADAPTATION_MODES] = current
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.name
        }
    }

    suspend fun setUiScale(scale: Float) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.UI_SCALE] = scale.coerceIn(0.80f, 1.30f)
        }
    }

    suspend fun setPlayerCoverStyle(style: PlayerCoverStyle) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.PLAYER_COVER_STYLE] = style.name
        }
    }

    suspend fun togglePinRecentPackage(packageName: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_RECENTS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(packageName)) {
                current.remove(packageName)
            }
            prefs[Keys.PINNED_RECENTS] = current
        }
    }

    suspend fun setOemGestureBypassEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.OEM_GESTURE_BYPASS] = enabled
        }
    }
}
