package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AlbumExpectedTrack
import com.example.model.AlphabetMode
import com.example.model.AppIconMode
import com.example.model.AppIconOverride
import com.example.model.AppLanguage
import com.example.model.CustomColorPalette
import com.example.model.EqualizerPreset
import com.example.model.GitHubBuildConfig
import com.example.model.HubQuickLink
import com.example.model.HubSubTab
import com.example.model.MediaQueueTrack
import com.example.model.PlayerCoverStyle
import com.example.model.SmartAlbum
import com.example.model.SourceSelectionMode
import com.example.model.TrackLyricsData
import com.example.model.UiThemeColor
import com.example.model.WeatherCityResult
import com.example.model.WorkspaceCell
import com.example.model.WorkspaceGridState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject

private val Context.launcherDataStore by preferencesDataStore(name = "keydeck_launcher_prefs")

class LauncherPreferencesRepository(private val context: Context) {

    private object Keys {
        val PINNED_APPS = stringSetPreferencesKey("pinned_apps")
        val ALPHABET_MODE = stringPreferencesKey("alphabet_mode")
        val PREFERRED_MEDIA_PKG = stringPreferencesKey("preferred_media_pkg")
        val HUB_LINKS = stringSetPreferencesKey("hub_quick_links")
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
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val UI_SCALE = floatPreferencesKey("ui_scale")
        val PLAYER_COVER_STYLE = stringPreferencesKey("player_cover_style")
        val PLAYER_COLLAPSED = booleanPreferencesKey("player_collapsed")
        val PINNED_RECENTS = stringSetPreferencesKey("pinned_recents")
        val OEM_GESTURE_BYPASS = booleanPreferencesKey("oem_gesture_bypass")
        val WORKSPACES_JSON = stringPreferencesKey("workspaces_grid_json")
        val SAVED_PALETTES_JSON = stringPreferencesKey("saved_palettes_json")
        val ACTIVE_PALETTE_ID = stringPreferencesKey("active_palette_id")
        val WEATHER_CITY_JSON = stringPreferencesKey("weather_city_json")
        val STREAMING_HISTORY_JSON = stringPreferencesKey("streaming_history_json")
        val STREAM_SEARCH_HISTORY = stringSetPreferencesKey("stream_search_history")
        val SHOW_WEATHER_IN_HUB_OTHER = booleanPreferencesKey("show_weather_in_hub_other")
        val SHOW_WEATHER_IN_HOME_TILE = booleanPreferencesKey("show_weather_in_home_tile")
        val MUSIC_SOURCE_MODE = stringPreferencesKey("music_source_mode")
        val SMART_ALBUMS_JSON = stringPreferencesKey("smart_albums_json")
        val LYRICS_OVERRIDES_JSON = stringPreferencesKey("lyrics_overrides_json")
        val EQUALIZER_JSON = stringPreferencesKey("equalizer_settings_json")
        val INSTALLED_LINUX_TILES_JSON = stringPreferencesKey("installed_linux_tiles_json")
        val GITHUB_BUILD_REPO = stringPreferencesKey("github_build_repo")
        val GITHUB_BUILD_BRANCH = stringPreferencesKey("github_build_branch")
        val GITHUB_BUILD_TOKEN = stringPreferencesKey("github_build_token")
        val TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen_v25")
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

    val isPlayerCollapsed: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.PLAYER_COLLAPSED] ?: false }
        .onStart { emit(false) }

    val pinnedRecentPackages: Flow<Set<String>> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.PINNED_RECENTS] ?: emptySet() }
        .onStart { emit(emptySet()) }

    val oemGestureBypassEnabled: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.OEM_GESTURE_BYPASS] ?: false }
        .onStart { emit(false) }

    val workspaceGridState: Flow<WorkspaceGridState> = context.launcherDataStore.data
        .map { prefs -> parseWorkspaceGrid(prefs[Keys.WORKSPACES_JSON]) }
        .onStart { emit(WorkspaceGridState()) }

    val savedPalettes: Flow<List<CustomColorPalette>> = context.launcherDataStore.data
        .map { prefs -> parsePalettes(prefs[Keys.SAVED_PALETTES_JSON]) }
        .onStart { emit(emptyList()) }

    val activeCustomPaletteId: Flow<String?> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.ACTIVE_PALETTE_ID]?.takeIf { it.isNotBlank() } }
        .onStart { emit(null) }

    val selectedWeatherCity: Flow<WeatherCityResult> = context.launcherDataStore.data
        .map { prefs -> parseWeatherCity(prefs[Keys.WEATHER_CITY_JSON]) }
        .onStart { emit(DEFAULT_WEATHER_CITY) }

    val streamingHistory: Flow<List<MediaQueueTrack>> = context.launcherDataStore.data
        .map { prefs -> parseStreamingHistory(prefs[Keys.STREAMING_HISTORY_JSON]) }
        .onStart { emit(emptyList()) }

    val streamSearchHistory: Flow<List<String>> = context.launcherDataStore.data
        .map { prefs -> (prefs[Keys.STREAM_SEARCH_HISTORY] ?: emptySet()).toList().sorted() }
        .onStart { emit(emptyList()) }

    val showWeatherInHubOther: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.SHOW_WEATHER_IN_HUB_OTHER] ?: true }
        .onStart { emit(true) }

    val showWeatherInHomeTile: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.SHOW_WEATHER_IN_HOME_TILE] ?: false }
        .onStart { emit(false) }

    val musicSourceMode: Flow<SourceSelectionMode> = context.launcherDataStore.data
        .map { prefs ->
            runCatching {
                SourceSelectionMode.valueOf(prefs[Keys.MUSIC_SOURCE_MODE].orEmpty())
            }.getOrDefault(SourceSelectionMode.AUTO)
        }
        .onStart { emit(SourceSelectionMode.AUTO) }

    val smartAlbums: Flow<List<SmartAlbum>> = context.launcherDataStore.data
        .map { prefs -> parseSmartAlbums(prefs[Keys.SMART_ALBUMS_JSON]) }
        .onStart { emit(emptyList()) }

    val lyricsOverrides: Flow<Map<String, TrackLyricsData>> = context.launcherDataStore.data
        .map { prefs -> parseLyricsMap(prefs[Keys.LYRICS_OVERRIDES_JSON]) }
        .onStart { emit(emptyMap()) }

    val equalizerConfig: Flow<SavedEqualizerConfig?> = context.launcherDataStore.data
        .map { prefs -> parseEqualizerConfig(prefs[Keys.EQUALIZER_JSON]) }
        .onStart { emit(null) }

    val installedLinuxTiles: Flow<List<PersistedLinuxTile>> = context.launcherDataStore.data
        .map { prefs -> parseInstalledLinuxTiles(prefs[Keys.INSTALLED_LINUX_TILES_JSON]) }
        .onStart { emit(emptyList()) }

    val githubBuildConfig: Flow<GitHubBuildConfig> = context.launcherDataStore.data
        .map { prefs ->
            GitHubBuildConfig(
                repoSlug = prefs[Keys.GITHUB_BUILD_REPO].orEmpty(),
                branch = prefs[Keys.GITHUB_BUILD_BRANCH]?.ifBlank { "main" } ?: "main",
                token = prefs[Keys.GITHUB_BUILD_TOKEN].orEmpty()
            )
        }
        .onStart { emit(GitHubBuildConfig()) }

    val tutorialSeen: Flow<Boolean> = context.launcherDataStore.data
        .map { prefs -> prefs[Keys.TUTORIAL_SEEN] ?: false }
        .onStart { emit(false) }

    suspend fun setTutorialSeen(seen: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.TUTORIAL_SEEN] = seen
        }
    }

    suspend fun saveGitHubBuildConfig(config: GitHubBuildConfig) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.GITHUB_BUILD_REPO] = config.repoSlug
            prefs[Keys.GITHUB_BUILD_BRANCH] = config.branch
            prefs[Keys.GITHUB_BUILD_TOKEN] = config.token
        }
    }

    suspend fun upsertInstalledLinuxTile(tile: PersistedLinuxTile) {
        context.launcherDataStore.edit { prefs ->
            val current = parseInstalledLinuxTiles(prefs[Keys.INSTALLED_LINUX_TILES_JSON])
                .filterNot { it.id == tile.id || it.packageName.equals(tile.packageName, ignoreCase = true) }
            prefs[Keys.INSTALLED_LINUX_TILES_JSON] = serializeInstalledLinuxTiles(listOf(tile) + current)
        }
    }

    suspend fun removeInstalledLinuxTile(tileId: String) {
        context.launcherDataStore.edit { prefs ->
            val current = parseInstalledLinuxTiles(prefs[Keys.INSTALLED_LINUX_TILES_JSON])
                .filterNot { it.id == tileId }
            prefs[Keys.INSTALLED_LINUX_TILES_JSON] = serializeInstalledLinuxTiles(current)
        }
    }

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
        if (context.launcherDataStore.data.first()[Keys.AUTO_KILL_INITIALIZED] == true) return
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
        if (context.launcherDataStore.data.first()[Keys.BG_SPOOF_INITIALIZED] == true) return
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

    suspend fun setPlayerCollapsed(collapsed: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.PLAYER_COLLAPSED] = collapsed
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

    private fun serializeWorkspaceGrid(state: WorkspaceGridState): String {
        val cellsWithHome = if (state.cells.any { it.x == 0 && it.y == 0 }) {
            state.cells
        } else {
            listOf(WorkspaceCell(0, 0)) + state.cells
        }
        val obj = JSONObject().apply {
            put("activeX", state.activeX)
            put("activeY", state.activeY)
            val arr = JSONArray()
            cellsWithHome.forEach { cell ->
                arr.put(
                    JSONObject().apply {
                        put("x", cell.x)
                        put("y", cell.y)
                        cell.packageName?.let { put("pkg", it) }
                        cell.activityName?.let { put("act", it) }
                        cell.label?.let { put("lbl", it) }
                    }
                )
            }
            put("cells", arr)
        }
        return obj.toString()
    }

    suspend fun saveWorkspaceGridState(state: WorkspaceGridState) {
        val raw = serializeWorkspaceGrid(state)
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.WORKSPACES_JSON] = raw
        }
    }

    suspend fun updateWorkspaceGridState(transform: (WorkspaceGridState) -> WorkspaceGridState): WorkspaceGridState {
        var result = WorkspaceGridState()
        context.launcherDataStore.edit { prefs ->
            val current = parseWorkspaceGrid(prefs[Keys.WORKSPACES_JSON])
            result = transform(current)
            prefs[Keys.WORKSPACES_JSON] = serializeWorkspaceGrid(result)
        }
        return result
    }

    suspend fun saveCustomPalette(palette: CustomColorPalette, activate: Boolean = true) {
        context.launcherDataStore.edit { prefs ->
            val list = parsePalettes(prefs[Keys.SAVED_PALETTES_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.id == palette.id }
            if (idx >= 0) list[idx] = palette else list.add(0, palette)
            prefs[Keys.SAVED_PALETTES_JSON] = serializePalettes(list.take(18))
            if (activate) {
                prefs[Keys.ACTIVE_PALETTE_ID] = palette.id
            }
        }
    }

    suspend fun setActiveCustomPaletteId(paletteId: String?) {
        context.launcherDataStore.edit { prefs ->
            if (paletteId.isNullOrBlank()) {
                prefs.remove(Keys.ACTIVE_PALETTE_ID)
            } else {
                prefs[Keys.ACTIVE_PALETTE_ID] = paletteId
            }
        }
    }

    suspend fun deleteCustomPalette(paletteId: String) {
        context.launcherDataStore.edit { prefs ->
            val list = parsePalettes(prefs[Keys.SAVED_PALETTES_JSON]).filterNot { it.id == paletteId }
            prefs[Keys.SAVED_PALETTES_JSON] = serializePalettes(list)
            if (prefs[Keys.ACTIVE_PALETTE_ID] == paletteId) {
                prefs.remove(Keys.ACTIVE_PALETTE_ID)
            }
        }
    }

    suspend fun deduplicateCustomPalettes(): Int {
        var removedCount = 0
        context.launcherDataStore.edit { prefs ->
            val list = parsePalettes(prefs[Keys.SAVED_PALETTES_JSON])
            val seenColors = HashSet<Triple<Long, Long, Long>>()
            val deduplicated = mutableListOf<CustomColorPalette>()
            for (pal in list) {
                val key = Triple(pal.dominantDarkArgb, pal.accentArgb, pal.highlightArgb)
                if (seenColors.add(key)) {
                    deduplicated.add(pal)
                } else {
                    removedCount++
                }
            }
            if (removedCount > 0) {
                prefs[Keys.SAVED_PALETTES_JSON] = serializePalettes(deduplicated)
                val activeId = prefs[Keys.ACTIVE_PALETTE_ID]
                if (activeId != null && deduplicated.none { it.id == activeId }) {
                    prefs.remove(Keys.ACTIVE_PALETTE_ID)
                }
            }
        }
        return removedCount
    }

    suspend fun setSelectedWeatherCity(city: WeatherCityResult) {
        val obj = JSONObject().apply {
            put("name", city.name)
            put("country", city.country)
            put("admin", city.admin)
            put("lat", city.latitude)
            put("lon", city.longitude)
        }
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.WEATHER_CITY_JSON] = obj.toString()
        }
    }

    suspend fun saveStreamingHistory(tracks: List<MediaQueueTrack>) {
        val arr = JSONArray()
        tracks.take(40).forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.queueId)
                    put("title", t.title)
                    put("sub", t.subtitle)
                    put("pkg", t.packageName)
                    put("app", t.appLabel)
                    t.mediaId?.let { put("mid", it) }
                }
            )
        }
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.STREAMING_HISTORY_JSON] = arr.toString()
        }
    }

    suspend fun addStreamSearchHistory(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        context.launcherDataStore.edit { prefs ->
            val cur = (prefs[Keys.STREAM_SEARCH_HISTORY] ?: emptySet()).toMutableSet()
            cur.add(q)
            if (cur.size > 20) {
                val trimmed = cur.drop(cur.size - 20).toSet()
                prefs[Keys.STREAM_SEARCH_HISTORY] = trimmed
            } else {
                prefs[Keys.STREAM_SEARCH_HISTORY] = cur
            }
        }
    }

    suspend fun setShowWeatherInHubOther(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SHOW_WEATHER_IN_HUB_OTHER] = enabled
        }
    }

    suspend fun setShowWeatherInHomeTile(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SHOW_WEATHER_IN_HOME_TILE] = enabled
        }
    }

    suspend fun setMusicSourceMode(mode: SourceSelectionMode) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.MUSIC_SOURCE_MODE] = mode.name
        }
    }

    suspend fun saveSmartAlbums(albums: List<SmartAlbum>) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SMART_ALBUMS_JSON] = serializeSmartAlbums(albums)
        }
    }

    suspend fun saveLyricsOverride(lyrics: TrackLyricsData) {
        context.launcherDataStore.edit { prefs ->
            val map = parseLyricsMap(prefs[Keys.LYRICS_OVERRIDES_JSON]).toMutableMap()
            map[lyrics.trackKey] = lyrics
            if (map.size > 60) {
                val trimKey = map.keys.firstOrNull()
                if (trimKey != null && trimKey != lyrics.trackKey) map.remove(trimKey)
            }
            prefs[Keys.LYRICS_OVERRIDES_JSON] = serializeLyricsMap(map)
        }
    }

    suspend fun saveEqualizerConfig(config: SavedEqualizerConfig) {
        val obj = JSONObject().apply {
            put("en", config.enabled)
            put("pre", config.preset.name)
            put("bass", config.bassBoost)
            put("virt", config.virtualizer)
            val arr = JSONArray()
            config.bandsMb.forEach { arr.put(it) }
            put("bands", arr)
        }
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.EQUALIZER_JSON] = obj.toString()
        }
    }

    private fun parseWorkspaceGrid(raw: String?): WorkspaceGridState {
        if (raw.isNullOrBlank()) return WorkspaceGridState()
        return runCatching {
            val obj = JSONObject(raw)
            val activeX = obj.optInt("activeX", 0)
            val activeY = obj.optInt("activeY", 0)
            val arr = obj.optJSONArray("cells") ?: JSONArray()
            val list = mutableListOf<WorkspaceCell>()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                list.add(
                    WorkspaceCell(
                        x = c.optInt("x", 0),
                        y = c.optInt("y", 0),
                        packageName = c.optString("pkg", "").takeIf { it.isNotBlank() },
                        activityName = c.optString("act", "").takeIf { it.isNotBlank() },
                        label = c.optString("lbl", "").takeIf { it.isNotBlank() }
                    )
                )
            }
            if (list.none { it.x == 0 && it.y == 0 }) {
                list.add(0, WorkspaceCell(0, 0))
            }
            val safeActive = if (list.any { it.x == activeX && it.y == activeY }) activeX to activeY else 0 to 0
            WorkspaceGridState(cells = list.distinctBy { it.x to it.y }, activeX = safeActive.first, activeY = safeActive.second)
        }.getOrDefault(WorkspaceGridState())
    }

    private fun parsePalettes(raw: String?): List<CustomColorPalette> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        CustomColorPalette(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            dominantDarkArgb = o.optLong("dark"),
                            accentArgb = o.optLong("accent"),
                            highlightArgb = o.optLong("hl"),
                            isFromWallpaper = o.optBoolean("wall", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializePalettes(list: List<CustomColorPalette>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("dark", p.dominantDarkArgb)
                    put("accent", p.accentArgb)
                    put("hl", p.highlightArgb)
                    put("wall", p.isFromWallpaper)
                }
            )
        }
        return arr.toString()
    }

    private fun parseWeatherCity(raw: String?): WeatherCityResult {
        if (raw.isNullOrBlank()) return DEFAULT_WEATHER_CITY
        return runCatching {
            val o = JSONObject(raw)
            WeatherCityResult(
                name = o.optString("name", DEFAULT_WEATHER_CITY.name),
                country = o.optString("country", ""),
                admin = o.optString("admin", ""),
                latitude = o.optDouble("lat", DEFAULT_WEATHER_CITY.latitude),
                longitude = o.optDouble("lon", DEFAULT_WEATHER_CITY.longitude)
            )
        }.getOrDefault(DEFAULT_WEATHER_CITY)
    }

    private fun parseStreamingHistory(raw: String?): List<MediaQueueTrack> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        MediaQueueTrack(
                            queueId = o.optLong("id", i.toLong()),
                            title = o.optString("title"),
                            subtitle = o.optString("sub"),
                            mediaId = o.optString("mid", "").takeIf { it.isNotBlank() },
                            packageName = o.optString("pkg"),
                            appLabel = o.optString("app"),
                            isActive = false
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeSmartAlbums(albums: List<SmartAlbum>): String {
        val arr = JSONArray()
        albums.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("title", a.title)
                    put("artist", a.artist)
                    put("rid", a.releaseId)
                    put("expCnt", a.expectedTrackCount)
                    a.coverUrl?.let { put("cov", it) }
                    a.customCoverPath?.let { put("custCov", it) }
                    val altArr = JSONArray()
                    a.alternateCoverUrls.forEach { altArr.put(it) }
                    put("altCov", altArr)
                    val attArr = JSONArray()
                    a.attachedLocalTrackIds.forEach { attArr.put(it) }
                    put("attIds", attArr)
                    val expArr = JSONArray()
                    a.expectedTracks.forEach { t ->
                        expArr.put(
                            JSONObject().apply {
                                put("num", t.trackNumber)
                                put("title", t.title)
                                put("art", t.artist)
                                put("dur", t.durationMs)
                                t.matchedLocalTrackId?.let { put("mid", it) }
                                t.matchedLocalTitle?.let { put("mlt", it) }
                                t.matchedFormat?.let { put("mfmt", it) }
                            }
                        )
                    }
                    put("exp", expArr)
                }
            )
        }
        return arr.toString()
    }

    private fun parseSmartAlbums(raw: String?): List<SmartAlbum> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val altCov = buildList {
                        val a = o.optJSONArray("altCov") ?: JSONArray()
                        for (j in 0 until a.length()) {
                            a.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                    val attIds = buildList {
                        val a = o.optJSONArray("attIds") ?: JSONArray()
                        for (j in 0 until a.length()) {
                            a.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                    val expTracks = buildList {
                        val a = o.optJSONArray("exp") ?: JSONArray()
                        for (j in 0 until a.length()) {
                            val to = a.optJSONObject(j) ?: continue
                            add(
                                AlbumExpectedTrack(
                                    trackNumber = to.optInt("num", j + 1),
                                    title = to.optString("title"),
                                    artist = to.optString("art"),
                                    durationMs = to.optLong("dur", 0L),
                                    matchedLocalTrackId = to.optString("mid", "").takeIf { it.isNotBlank() },
                                    matchedLocalTitle = to.optString("mlt", "").takeIf { it.isNotBlank() },
                                    matchedFormat = to.optString("mfmt", "").takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    }
                    add(
                        SmartAlbum(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            artist = o.optString("artist"),
                            releaseId = o.optString("rid"),
                            expectedTrackCount = o.optInt("expCnt", expTracks.size),
                            coverUrl = o.optString("cov", "").takeIf { it.isNotBlank() },
                            alternateCoverUrls = altCov,
                            customCoverPath = o.optString("custCov", "").takeIf { it.isNotBlank() },
                            attachedLocalTrackIds = attIds,
                            expectedTracks = expTracks
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeLyricsMap(map: Map<String, TrackLyricsData>): String {
        val arr = JSONArray()
        map.values.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("key", item.trackKey)
                    put("title", item.title)
                    put("art", item.artist)
                    put("raw", item.rawLrc)
                    put("plain", item.plainText)
                    put("src", item.sourceLabel)
                    put("del", item.isDeleted)
                    put("edt", item.isCustomEdited)
                }
            )
        }
        return arr.toString()
    }

    private fun parseLyricsMap(raw: String?): Map<String, TrackLyricsData> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val key = o.optString("key").trim()
                    if (key.isEmpty()) continue
                    val rawLrc = o.optString("raw", "")
                    val synced = MusicCatalogAndLyricsRepository.parseLrc(rawLrc)
                    put(
                        key,
                        TrackLyricsData(
                            trackKey = key,
                            title = o.optString("title"),
                            artist = o.optString("art"),
                            syncedLines = synced,
                            plainText = o.optString("plain"),
                            rawLrc = rawLrc,
                            sourceLabel = o.optString("src", "LRC"),
                            isDeleted = o.optBoolean("del", false),
                            isCustomEdited = o.optBoolean("edt", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun parseEqualizerConfig(raw: String?): SavedEqualizerConfig? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            val preset = runCatching { EqualizerPreset.valueOf(o.optString("pre")) }.getOrDefault(EqualizerPreset.FLAT)
            val bands = buildList {
                val arr = o.optJSONArray("bands") ?: JSONArray()
                for (i in 0 until arr.length()) add(arr.optInt(i, 0))
            }
            SavedEqualizerConfig(
                enabled = o.optBoolean("en", true),
                preset = preset,
                bandsMb = bands,
                bassBoost = o.optInt("bass", 250),
                virtualizer = o.optInt("virt", 180)
            )
        }.getOrNull()
    }

    data class SavedEqualizerConfig(
        val enabled: Boolean,
        val preset: EqualizerPreset,
        val bandsMb: List<Int>,
        val bassBoost: Int,
        val virtualizer: Int
    )

    data class PersistedLinuxTile(
        val id: String,
        val name: String,
        val packageName: String,
        val kind: String,
        val installedDirPath: String,
        val entryCommand: String,
        val version: String = "1.0"
    )

    private fun serializeInstalledLinuxTiles(tiles: List<PersistedLinuxTile>): String {
        val arr = JSONArray()
        tiles.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("pkg", t.packageName)
                    put("kind", t.kind)
                    put("dir", t.installedDirPath)
                    put("cmd", t.entryCommand)
                    put("ver", t.version)
                }
            )
        }
        return arr.toString()
    }

    private fun parseInstalledLinuxTiles(raw: String?): List<PersistedLinuxTile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    if (id.isEmpty()) continue
                    add(
                        PersistedLinuxTile(
                            id = id,
                            name = o.optString("name", id),
                            packageName = o.optString("pkg", id),
                            kind = o.optString("kind", "DEB"),
                            installedDirPath = o.optString("dir", ""),
                            entryCommand = o.optString("cmd", ""),
                            version = o.optString("ver", "1.0")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        val DEFAULT_WEATHER_CITY = WeatherCityResult(
            name = "Москва",
            country = "Россия",
            admin = "Москва",
            latitude = 55.7558,
            longitude = 37.6173
        )
    }
}
