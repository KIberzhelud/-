package com.example.model

enum class ZipArchiveKind(val title: String) {
    APK_UPDATE("Обновление KeyDeck (APK)"),
    APK_SPLIT_BUNDLE("Пакет APK / Split-APKs"),
    FOREIGN_APK("APK другого приложения"),
    KEYDECK_CONFIG_PACK("Пакет настроек, тем и текстов"),
    SOURCE_CODE_ARCHIVE("Исходный код (ZIP из AI Studio)"),
    UNSUPPORTED("Неподдерживаемый или пустой архив")
}

data class GitHubBuildConfig(
    val repoSlug: String = "",
    val branch: String = "main",
    val token: String = ""
) {
    val isConfigured: Boolean
        get() = repoSlug.contains("/") && repoSlug.substringBefore("/").isNotBlank() &&
            repoSlug.substringAfter("/").isNotBlank() && token.isNotBlank()
}

data class ZipUpdateInspection(
    val sourceUriString: String,
    val localFilePath: String? = null,
    val archiveName: String,
    val archiveSizeBytes: Long = 0L,
    val kind: ZipArchiveKind,
    val isSamePackageUpdate: Boolean = false,
    val targetPackageName: String? = null,
    val targetAppLabel: String? = null,
    val targetVersionName: String? = null,
    val targetVersionCode: Long? = null,
    val currentVersionName: String = "",
    val currentVersionCode: Long = 0L,
    val extractedApkPaths: List<String> = emptyList(),
    val hasConfigJson: Boolean = false,
    val changelog: String = "",
    val paletteCount: Int = 0,
    val quickLinkCount: Int = 0,
    val lrcCount: Int = 0,
    val hasWallpaper: Boolean = false,
    val sourceCodeFileCount: Int = 0,
    val hasGradleFiles: Boolean = false,
    val hasSigningKeystoreInZip: Boolean = false,
    val totalEntriesCount: Int = 0,
    val summaryMessage: String = "",
    val pendingPalettes: List<CustomColorPalette> = emptyList(),
    val pendingQuickLinks: List<HubQuickLink> = emptyList(),
    val pendingLyrics: List<TrackLyricsData> = emptyList(),
    val extractedWallpaperPath: String? = null
) {
    val canInstallApks: Boolean
        get() = extractedApkPaths.isNotEmpty()

    val canApplyConfigPack: Boolean
        get() = paletteCount > 0 || quickLinkCount > 0 || lrcCount > 0 || hasWallpaper
}
