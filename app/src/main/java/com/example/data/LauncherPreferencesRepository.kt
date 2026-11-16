package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AlphabetMode
import com.example.model.AppIconMode
import com.example.model.AppIconOverride
import com.example.model.HubQuickLink
import com.example.model.HubSubTab
import kotlinx.coroutines.flow.Flow
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
}
