package com.example.model

enum class MusicSourceKind(val title: String, val priorityOrder: Int) {
    LOCAL_FLAC("FLAC Локально", 0),
    ARCHIVE_FLAC("FLAC Интернет", 1),
    DISCOGS_CATALOG("Discogs.com", 2),
    STREAMING_PAID("Стриминг", 3),
    TELEGRAM_AUDIO("Telegram Аудио", 4),
    LOCAL_FILE("Файл устройства", 5),
    ARCHIVE_LOSSY("Интернет Аудио", 6),
    ITUNES_CATALOG("Каталог", 7)
}

enum class SourceSelectionMode(val title: String) {
    AUTO("Авто (FLAC → Стриминг)"),
    PICK("Выбор источника")
}

enum class RepeatMode(val title: String) {
    OFF("Выкл"),
    ALL("Все"),
    ONE("1 трек")
}

enum class PowerampTab(val title: String) {
    PLAYER("Плеер"),
    EQUALIZER("EQ"),
    LYRICS("Текст"),
    SEARCH("Поиск"),
    ALBUMS("Альбомы"),
    LIBRARY("Файлы")
}

data class TrackSourceCandidate(
    val id: String,
    val sourceKind: MusicSourceKind,
    val sourceLabel: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val packageName: String? = null,
    val downloadUrl: String? = null,
    val previewUrl: String? = null,
    val fileExtension: String = "",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val sizeBytes: Long = 0L,
    val artworkUrl: String? = null,
    val localTrack: LocalAudioTrack? = null,
    val mediaBrowserTrack: MediaBrowserTrack? = null,
    val queueItemId: Long? = null
) {
    val isLossless: Boolean
        get() = fileExtension.equals("flac", true) ||
            fileExtension.equals("wav", true) ||
            sourceKind == MusicSourceKind.LOCAL_FLAC ||
            sourceKind == MusicSourceKind.ARCHIVE_FLAC

    val formatBadge: String
        get() = when {
            fileExtension.equals("flac", true) || sourceKind == MusicSourceKind.LOCAL_FLAC || sourceKind == MusicSourceKind.ARCHIVE_FLAC -> "FLAC"
            fileExtension.equals("wav", true) -> "WAV"
            fileExtension.equals("ogg", true) || fileExtension.equals("opus", true) -> fileExtension.uppercase()
            fileExtension.equals("m4a", true) || fileExtension.equals("aac", true) -> fileExtension.uppercase()
            fileExtension.equals("mp3", true) -> "MP3"
            sourceKind == MusicSourceKind.STREAMING_PAID -> "STREAM"
            sourceKind == MusicSourceKind.TELEGRAM_AUDIO -> "TG"
            else -> "AUDIO"
        }
}

data class UnifiedMusicSearchResult(
    val key: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val candidates: List<TrackSourceCandidate>
) {
    val primaryCandidate: TrackSourceCandidate?
        get() = candidates.firstOrNull()

    val hasMultipleSources: Boolean
        get() = candidates.map { "${it.sourceKind}:${it.sourceLabel}" }.distinct().size > 1

    val bestFormatBadge: String
        get() = primaryCandidate?.formatBadge ?: "AUDIO"
}

data class PreviewPlaybackState(
    val candidateId: String? = null,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null
)

data class SyncedLyricLine(
    val timeMs: Long,
    val text: String
)

data class TrackLyricsData(
    val trackKey: String,
    val title: String,
    val artist: String,
    val syncedLines: List<SyncedLyricLine> = emptyList(),
    val plainText: String = "",
    val rawLrc: String = "",
    val sourceLabel: String = "LRCLIB",
    val isDeleted: Boolean = false,
    val isCustomEdited: Boolean = false,
    val isConfirmedPrimary: Boolean = false
) {
    val hasSynced: Boolean get() = syncedLines.isNotEmpty()
    val hasAnyLyrics: Boolean get() = !isDeleted && (syncedLines.isNotEmpty() || plainText.isNotBlank() || rawLrc.isNotBlank())
}

enum class EqualizerPreset(
    val title: String,
    val bandLevelsMb: List<Int>,
    val bassBoost: Int,
    val virtualizer: Int
) {
    FLAT("Плоский", listOf(0, 0, 0, 0, 0), 120, 100),
    ROCK("Poweramp Рок", listOf(550, 220, -150, 380, 620), 420, 300),
    BASS_VOCAL("Бас + Вокал", listOf(680, 150, 320, 440, 180), 650, 220),
    ELECTRONIC("Электро", listOf(620, 350, -180, 290, 580), 560, 420),
    JAZZ("Джаз", listOf(350, 120, -100, 240, 420), 220, 350),
    CLASSICAL("Классика", listOf(420, 240, 0, 260, 460), 140, 480)
}

data class EqualizerBandUi(
    val index: Int,
    val freqLabel: String,
    val levelMb: Int
)

data class EqualizerState(
    val enabled: Boolean = true,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val bands: List<EqualizerBandUi> = defaultBands(),
    val bassBoostStrength: Int = 250,
    val virtualizerStrength: Int = 180,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val dspHardwareAttached: Boolean = false
) {
    companion object {
        fun defaultBands(levels: List<Int> = listOf(0, 0, 0, 0, 0)): List<EqualizerBandUi> {
            val labels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
            return labels.mapIndexed { idx, lbl ->
                EqualizerBandUi(
                    index = idx,
                    freqLabel = lbl,
                    levelMb = levels.getOrElse(idx) { 0 }.coerceIn(-1500, 1500)
                )
            }
        }
    }
}

data class AudioCaptureState(
    val isCapturing: Boolean = false,
    val targetTitle: String = "",
    val targetArtist: String = "",
    val targetPackage: String = "",
    val targetAlbumId: String? = null,
    val expectedDurationMs: Long = 0L,
    val progressPercent: Int = 0,
    val autoSplitEnabled: Boolean = false,
    val silentMuteMode: Boolean = false,
    val capturedFilesCount: Int = 0,
    val discardReason: String? = null,
    val elapsedSec: Int = 0,
    val bytesWritten: Long = 0L,
    val lastSavedPath: String? = null,
    val statusText: String = ""
)

data class AlbumExpectedTrack(
    val trackNumber: Int,
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0L,
    val matchedLocalTrackId: String? = null,
    val matchedLocalTitle: String? = null,
    val matchedFormat: String? = null
) {
    val isMatched: Boolean get() = !matchedLocalTrackId.isNullOrBlank()
}

data class SmartAlbum(
    val id: String,
    val title: String,
    val artist: String = "",
    val releaseId: String = "",
    val expectedTrackCount: Int = 0,
    val coverUrl: String? = null,
    val alternateCoverUrls: List<String> = emptyList(),
    val customCoverPath: String? = null,
    val attachedLocalTrackIds: List<String> = emptyList(),
    val expectedTracks: List<AlbumExpectedTrack> = emptyList(),
    val isFetchingOnline: Boolean = false,
    val statusMessage: String = ""
) {
    val matchedCount: Int
        get() = if (expectedTracks.isNotEmpty()) {
            expectedTracks.count { it.isMatched }
        } else {
            attachedLocalTrackIds.size
        }

    val totalExpected: Int
        get() = expectedTrackCount.coerceAtLeast(expectedTracks.size).coerceAtLeast(attachedLocalTrackIds.size)

    val missingTracks: List<AlbumExpectedTrack>
        get() = expectedTracks.filter { !it.isMatched }

    val missingCount: Int
        get() = if (expectedTracks.isNotEmpty()) {
            missingTracks.size
        } else {
            (expectedTrackCount - attachedLocalTrackIds.size).coerceAtLeast(0)
        }
}
