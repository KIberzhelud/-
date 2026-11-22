package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AppEntry
import com.example.model.LocalAudioTrack
import com.example.model.MediaBrowserTrack
import com.example.model.MediaCustomActionItem
import com.example.model.MediaPlayerState
import com.example.model.MediaQueueTrack
import com.example.model.MediaSessionUi
import com.example.model.MusicStreamMatch
import com.example.model.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object MediaControlBus {
    private val _listenerEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val listenerEvents = _listenerEvents.asSharedFlow()

    fun notifyListenerChanged() {
        _listenerEvents.tryEmit(Unit)
    }
}

object MediaGuardRuntime {
    @Volatile
    var isGuardEnabled: Boolean = false

    @Volatile
    var guardedPackages: Set<String> = emptySet()

    @Volatile
    var lastUserPausedPackage: String? = null

    @Volatile
    var lastUserPausedAtMs: Long = 0L

    @Volatile
    var isScreenOff: Boolean = false

    @Volatile
    var visibleWindowPackages: Set<String> = emptySet()

    @Volatile
    var onUnexpectedPauseDetected: ((packageName: String) -> Unit)? = null

    private val wasPlayingPackages = ConcurrentHashMap<String, Boolean>()

    fun updateVisibleWindows(pkgs: Set<String>) {
        visibleWindowPackages = pkgs
    }

    fun recordUserPause(packageName: String) {
        lastUserPausedPackage = packageName
        lastUserPausedAtMs = SystemClock.elapsedRealtime()
        wasPlayingPackages[packageName] = false
    }

    fun recordUserPlay(packageName: String) {
        if (lastUserPausedPackage == packageName) {
            lastUserPausedPackage = null
        }
        wasPlayingPackages[packageName] = true
    }

    fun onSessionPlaybackState(packageName: String, state: Int) {
        val playingNow = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        val prevPlaying = wasPlayingPackages.put(packageName, playingNow) == true
        if (!isGuardEnabled || !guardedPackages.contains(packageName)) return

        if (prevPlaying && state == PlaybackState.STATE_PAUSED) {
            // Respect manual pause inside YouTube PiP / visible foreground window when screen is on
            if (!isScreenOff && visibleWindowPackages.contains(packageName)) {
                return
            }
            val elapsedSinceUserPause = SystemClock.elapsedRealtime() - lastUserPausedAtMs
            val isIntentionalUserPause = lastUserPausedPackage == packageName && elapsedSinceUserPause < 4500L
            if (!isIntentionalUserPause) {
                onUnexpectedPauseDetected?.invoke(packageName)
            }
        }
    }

    fun isDefaultSpoilerCandidate(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg.contains("youtube") ||
            pkg.contains("revanced") ||
            pkg.contains("vanced") ||
            pkg.contains("vkvideo") ||
            pkg == "com.vkontakte.android" ||
            pkg.contains("rutube") ||
            pkg.contains("newpipe") ||
            pkg.contains("twitch") ||
            pkg.contains("chrome") ||
            pkg.contains("yandex.browser") ||
            pkg.contains("opera")
    }
}

class MediaControlRepository(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val listenerComponent =
        ComponentName(context, LauncherMediaListenerService::class.java)

    private val controllers = ConcurrentHashMap<String, MediaController>()
    private val controllerCallbacks = ConcurrentHashMap<String, MediaController.Callback>()
    private val appLabelCache = ConcurrentHashMap<String, String>()

    private val _mediaState = MutableStateFlow(readInitialState())
    val mediaState: StateFlow<MediaPlayerState> = _mediaState.asStateFlow()

    private val _localTracks = MutableStateFlow<List<LocalAudioTrack>>(emptyList())
    val localTracks: StateFlow<List<LocalAudioTrack>> = _localTracks.asStateFlow()

    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    private var localPlayer: MediaPlayer? = null
    private var currentLocalTrack: LocalAudioTrack? = null
    private var localPrepared: Boolean = false
    private var localIsPlaying: Boolean = false
    private var localDurationMs: Long = 0L
    private var localPositionMs: Long = 0L
    private var localPositionUpdatedAtMs: Long = 0L
    private var localPlaybackSpeed: Float = 1.0f
    private var localRepeatMode: RepeatMode = RepeatMode.OFF
    private var localShuffleEnabled: Boolean = false

    private var localPlatformSession: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pauseLocalPlayerFromExternalSignal()
            }
        }
    }

    val dspController = AudioDspController()

    private var activeBrowser: MediaBrowser? = null
    private var activeBrowserPackage: String? = null
    private val recentTrackHistory = LinkedHashMap<String, MediaQueueTrack>()

    @Volatile
    var onTrackHistoryUpdated: ((List<MediaQueueTrack>) -> Unit)? = null

    fun loadPersistedHistory(saved: List<MediaQueueTrack>) {
        if (saved.isEmpty()) return
        var added = false
        saved.asReversed().forEach { item ->
            val key = "${item.packageName}|${item.title}|${item.subtitle}"
            if (!recentTrackHistory.containsKey(key) && item.title.isNotBlank()) {
                recentTrackHistory[key] = item.copy(isActive = false)
                added = true
            }
        }
        while (recentTrackHistory.size > 40) {
            val first = recentTrackHistory.keys.firstOrNull() ?: break
            recentTrackHistory.remove(first)
        }
        if (added) {
            val reversed = recentTrackHistory.values.toList().reversed()
            _mediaState.update { it.copy(recentTracks = reversed) }
        }
    }

    fun startObserving() {
        refreshListenerStatusAndSessions()
        if (activeBrowser == null) {
            val preferredPkg = resolveActiveController()?.packageName
                ?: if (resolveMediaBrowserComponent("ru.yandex.music") != null) "ru.yandex.music" else null
            connectMediaBrowserForActiveOrPackage(preferredPkg)
        }
    }

    fun stopObserving() {
        clearControllers()
        disconnectMediaBrowser()
        releaseLocalPlayer()
        releaseLocalPlatformSession()
        abandonLocalAudioFocus()
        dspController.releaseEffects()
        sessionsListener?.let { listener ->
            runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(listener) }
        }
        sessionsListener = null
    }

    suspend fun refreshLocalTracks(): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, LocalAudioTrack>()

        // 1. MediaStore Audio query
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            projection.add(MediaStore.Audio.Media.DATA)
        }

        runCatching {
            context.contentResolver.query(
                audioUri,
                projection.toTypedArray(),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 10000",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                }

                while (cursor.moveToNext() && found.size < 400) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                    val rawTitle = if (titleCol >= 0) cursor.getString(titleCol).orEmpty().trim() else ""
                    val title = rawTitle.ifEmpty { displayName.substringBeforeLast('.') }.ifEmpty { "Трек #$id" }
                    val rawArtist = if (artistCol >= 0) cursor.getString(artistCol).orEmpty().trim() else ""
                    val artist = if (rawArtist == "<unknown>") "" else rawArtist
                    val album = if (albumCol >= 0) cursor.getString(albumCol).orEmpty().trim() else ""
                    val dur = if (durCol >= 0) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                    val rel = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                    val contentUri = ContentUris.withAppendedId(audioUri, id).toString()

                    val key = "${title.lowercase()}|$dur"
                    val isTgMediaStore = rel.contains("telegram", ignoreCase = true)
                    val fmtLabel = displayName.substringAfterLast('.', "").uppercase()
                        .takeIf { it.isNotBlank() && it.length <= 5 } ?: "AUDIO"
                    found.putIfAbsent(
                        key,
                        LocalAudioTrack(
                            id = "ms_audio:$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = dur,
                            uriString = contentUri,
                            displayPath = if (rel.isNotEmpty()) "${rel.trimEnd('/')}/$displayName" else displayName,
                            isTelegramAudio = isTgMediaStore,
                            formatLabel = fmtLabel,
                            bitrateKbps = if (fmtLabel == "FLAC" || fmtLabel == "WAV") 1000 else 0,
                            sampleRateHz = if (fmtLabel == "FLAC" || fmtLabel == "WAV") 44100 else 0
                        )
                    )
                }
            }
        }

        // 2. Supplement: Always scan app-local & KeyDeck download folders; full external storage when permitted
        val hasAllFiles = UniversalSearchRepository.hasAllFilesAccess(context)
        val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        val keyDeckPublicMusic = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { File(it, "KeyDeck") }
        }.getOrNull()
        val dirs = buildList {
            add(File(context.filesDir, "music"))
            if (keyDeckPublicMusic != null) add(keyDeckPublicMusic)
            if (hasAllFiles && root != null) {
                add(File(root, "Telegram/Telegram Audio"))
                add(File(root, "Telegram/Telegram Documents"))
                add(File(root, "Music/Telegram"))
                add(File(root, "Download/Telegram"))
                add(File(root, "Android/media/org.telegram.messenger/Telegram/Telegram Audio"))
                add(File(root, "Android/media/com.exteragram.messenger/Telegram/Telegram Audio"))
                add(File(root, "Android/media/tw.nekomimi.nekogram/Telegram/Telegram Audio"))
                add(File(root, "Android/media/com.radolyn.ayugram/Telegram/Telegram Audio"))
                add(File(root, "Music"))
                add(File(root, "Download"))
                add(File(root, "Audio"))
            }
        }.distinctBy { it.absolutePath }.filter { it.exists() && it.isDirectory }

        if (dirs.isNotEmpty()) {
            val audioExts = setOf("mp3", "flac", "ogg", "m4a", "wav", "aac", "opus", "wma")
            val queue = ArrayDeque<File>(dirs)
            var visited = 0
            while (queue.isNotEmpty() && visited < 120 && found.size < 450) {
                val dir = queue.removeFirst()
                visited++
                dir.listFiles()?.forEach { child ->
                    if (child.name.startsWith(".")) return@forEach
                    if (child.isDirectory) {
                        queue.add(child)
                    } else if (child.isFile) {
                        val ext = child.extension.lowercase()
                        if (ext in audioExts) {
                            val title = child.nameWithoutExtension
                            val isTgFile = child.absolutePath.contains("telegram", ignoreCase = true) ||
                                child.absolutePath.contains("exteragram", ignoreCase = true) ||
                                child.absolutePath.contains("ayugram", ignoreCase = true) ||
                                child.absolutePath.contains("nekogram", ignoreCase = true)
                            val existingEntry = found.entries.firstOrNull {
                                it.value.filePath == child.absolutePath || it.value.title.equals(title, ignoreCase = true)
                            }
                            if (existingEntry == null) {
                                val meta = readAudioMetaFast(child)
                                val dur = meta.third
                                val key = "fs:${child.absolutePath}"
                                found.putIfAbsent(
                                    key,
                                    LocalAudioTrack(
                                        id = key,
                                        title = meta.first.ifBlank { title },
                                        artist = meta.second,
                                        album = if (isTgFile) "Telegram" else "",
                                        durationMs = dur,
                                        uriString = Uri.fromFile(child).toString(),
                                        filePath = child.absolutePath,
                                        displayPath = child.absolutePath,
                                        isTelegramAudio = isTgFile,
                                        formatLabel = ext.uppercase(),
                                        bitrateKbps = meta.fourth,
                                        sampleRateHz = meta.fifth
                                    )
                                )
                            } else {
                                var updated = existingEntry.value
                                if (updated.filePath.isNullOrBlank()) {
                                    updated = updated.copy(filePath = child.absolutePath)
                                }
                                if (isTgFile && !updated.isTelegramAudio) {
                                    updated = updated.copy(isTelegramAudio = true)
                                }
                                found[existingEntry.key] = updated
                            }
                        }
                    }
                }
            }
        }

        val sorted = found.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        _localTracks.value = sorted
        sorted
    }

    private data class AudioMetaResult(
        val first: String,
        val second: String,
        val third: Long,
        val fourth: Int,
        val fifth: Int
    )

    private fun readAudioMetaFast(file: File): AudioMetaResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 44100
            } else {
                44100
            }
            AudioMetaResult(title, artist, dur, (bitrateBps / 1000).coerceAtLeast(0), sampleRate)
        } catch (_: Throwable) {
            AudioMetaResult("", "", 0L, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun requestLocalAudioFocusAndPauseOthers() {
        // Pause any active external MediaControllers so only 1 audio source plays at once
        controllers.values.forEach { ctrl ->
            if (ctrl.packageName != context.packageName &&
                ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
            ) {
                MediaGuardRuntime.recordUserPause(ctrl.packageName)
                runCatching { ctrl.transportControls.pause() }
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                    .also { focusRequest = it }
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }

    private fun abandonLocalAudioFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusListener)
            }
        }
    }

    private fun pauseLocalPlayerFromExternalSignal() {
        val mp = localPlayer
        if (mp != null && localPrepared && localIsPlaying) {
            runCatching {
                localPositionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                localPositionUpdatedAtMs = SystemClock.elapsedRealtime()
                if (mp.isPlaying) mp.pause()
            }
            localIsPlaying = false
            syncLocalPlatformSessionAndNotification()
            rebuildSessionsSnapshot()
        }
    }

    private fun ensureLocalPlatformSession(): MediaSession? {
        localPlatformSession?.let { return it }
        return runCatching {
            MediaSession(context, "KeyDeckLocalPlayer").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        if (isLocalSessionActive() && !localIsPlaying) togglePlayPause()
                        else currentLocalTrack?.let { playLocalTrack(it) }
                    }
                    override fun onPause() {
                        if (localIsPlaying) pauseLocalPlayerFromExternalSignal()
                    }
                    override fun onSkipToNext() {
                        skipNext()
                    }
                    override fun onSkipToPrevious() {
                        skipPrevious()
                    }
                    override fun onSeekTo(pos: Long) {
                        seekTo(pos)
                    }
                    override fun onStop() {
                        stopPlayback()
                    }
                })
                isActive = true
            }.also { localPlatformSession = it }
        }.getOrNull()
    }

    private fun releaseLocalPlatformSession() {
        runCatching {
            localPlatformSession?.isActive = false
            localPlatformSession?.release()
        }
        localPlatformSession = null
        cancelLocalMediaNotification()
    }

    private fun syncLocalPlatformSessionAndNotification() {
        val track = currentLocalTrack
        if (track == null) {
            releaseLocalPlatformSession()
            return
        }
        val session = ensureLocalPlatformSession() ?: return
        val curPos = if (localPrepared && localPlayer != null) {
            runCatching { localPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L) }.getOrNull() ?: localPositionMs
        } else {
            localPositionMs
        }
        val dur = localDurationMs.coerceAtLeast(track.durationMs)
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_STOP

        val pbState = PlaybackState.Builder()
            .setActions(actions)
            .setState(
                if (localIsPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                curPos,
                if (localIsPlaying) localPlaybackSpeed else 0f,
                SystemClock.elapsedRealtime()
            )
            .build()

        val meta = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist.ifBlank { "KeyDeck Poweramp" })
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album.ifBlank { track.formatLabel })
            .putLong(MediaMetadata.METADATA_KEY_DURATION, dur)
            .build()

        runCatching {
            session.setMetadata(meta)
            session.setPlaybackState(pbState)
            session.isActive = true
        }
        postLocalMediaNotification(session, track)
    }

    private fun postLocalMediaNotification(session: MediaSession, track: LocalAudioTrack) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    LOCAL_MEDIA_CHANNEL_ID,
                    "KeyDeck • Локальный плеер",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
                nm.createNotificationChannel(ch)
            }
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val style = Notification.MediaStyle()
                .setMediaSession(session.sessionToken)
            val notif = Notification.Builder(context, LOCAL_MEDIA_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_symbian_music)
                .setContentTitle(track.title)
                .setContentText(track.artist.ifBlank { track.formatLabel })
                .setContentIntent(openIntent)
                .setOngoing(localIsPlaying)
                .setStyle(style)
                .build()
            nm.notify(LOCAL_MEDIA_NOTIF_ID, notif)
        }
    }

    private fun cancelLocalMediaNotification() {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return@runCatching
            nm.cancel(LOCAL_MEDIA_NOTIF_ID)
        }
    }

    fun playLocalTrack(track: LocalAudioTrack) {
        runCatching {
            requestLocalAudioFocusAndPauseOthers()
            localPlayer?.release()
            localPlayer = null
            localPrepared = false

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (!track.filePath.isNullOrBlank() && File(track.filePath).canRead()) {
                    setDataSource(track.filePath)
                } else {
                    setDataSource(context, Uri.parse(track.uriString))
                }
                setOnPreparedListener { mp ->
                    localPrepared = true
                    localDurationMs = mp.duration.toLong().coerceAtLeast(track.durationMs)
                    localPositionMs = 0L
                    localPositionUpdatedAtMs = SystemClock.elapsedRealtime()
                    localIsPlaying = true
                    runCatching { dspController.attachAudioSession(mp.audioSessionId) }
                    applyLocalPlaybackSpeed(mp, localPlaybackSpeed)
                    mp.start()
                    syncLocalPlatformSessionAndNotification()
                    rebuildSessionsSnapshot(forceSelectId = LOCAL_SESSION_ID)
                }
                setOnCompletionListener {
                    when (localRepeatMode) {
                        RepeatMode.ONE -> {
                            playLocalTrack(track)
                        }
                        RepeatMode.ALL -> {
                            val tracks = _localTracks.value
                            if (tracks.isEmpty()) {
                                localIsPlaying = false
                                syncLocalPlatformSessionAndNotification()
                                rebuildSessionsSnapshot()
                            } else if (localShuffleEnabled && tracks.size > 1) {
                                val next = tracks.filter { it.id != track.id }.randomOrNull() ?: tracks.first()
                                playLocalTrack(next)
                            } else {
                                val currentIdx = tracks.indexOfFirst { it.id == track.id }
                                val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % tracks.size
                                playLocalTrack(tracks[nextIdx])
                            }
                        }
                        RepeatMode.OFF -> {
                            val tracks = _localTracks.value
                            if (localShuffleEnabled && tracks.size > 1) {
                                val next = tracks.filter { it.id != track.id }.randomOrNull()
                                if (next != null) playLocalTrack(next)
                            } else {
                                val currentIdx = tracks.indexOfFirst { it.id == track.id }
                                if (currentIdx >= 0 && currentIdx + 1 < tracks.size) {
                                    playLocalTrack(tracks[currentIdx + 1])
                                } else {
                                    localIsPlaying = false
                                    localPositionMs = localDurationMs
                                    localPositionUpdatedAtMs = SystemClock.elapsedRealtime()
                                    syncLocalPlatformSessionAndNotification()
                                    rebuildSessionsSnapshot()
                                }
                            }
                        }
                    }
                }
                prepareAsync()
            }
            localPlayer = player
            currentLocalTrack = track
            localDurationMs = track.durationMs
            localPositionMs = 0L
            localPositionUpdatedAtMs = SystemClock.elapsedRealtime()
            localIsPlaying = true
            syncLocalPlatformSessionAndNotification()
            rebuildSessionsSnapshot(forceSelectId = LOCAL_SESSION_ID)
        }
    }

    fun setLocalPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.75f, 1.50f)
        localPlaybackSpeed = clamped
        dspController.setStateMeta(speed = clamped)
        localPlayer?.let { mp ->
            if (localPrepared) {
                applyLocalPlaybackSpeed(mp, clamped)
            }
        }
        rebuildSessionsSnapshot()
    }

    fun cycleLocalRepeatMode(): RepeatMode {
        localRepeatMode = when (localRepeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        dspController.setStateMeta(repeatMode = localRepeatMode)
        return localRepeatMode
    }

    fun toggleLocalShuffle(): Boolean {
        localShuffleEnabled = !localShuffleEnabled
        dspController.setStateMeta(shuffle = localShuffleEnabled)
        return localShuffleEnabled
    }

    private fun applyLocalPlaybackSpeed(mp: MediaPlayer, speed: Float) {
        runCatching {
            val wasPlaying = mp.isPlaying
            mp.playbackParams = mp.playbackParams.setSpeed(speed)
            if (!wasPlaying && mp.isPlaying) {
                mp.pause()
            }
        }
    }

    private fun isLocalSessionActive(): Boolean {
        return currentLocalTrack != null && _mediaState.value.activeSession?.id == LOCAL_SESSION_ID
    }

    private fun releaseLocalPlayer() {
        runCatching { localPlayer?.release() }
        localPlayer = null
        localPrepared = false
        localIsPlaying = false
    }

    suspend fun renameLocalTrack(track: LocalAudioTrack, newTitle: String): LocalAudioTrack? = withContext(Dispatchers.IO) {
        val cleanedTitle = newTitle.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (cleanedTitle.isEmpty()) return@withContext null

        var updatedTrack = track.copy(title = cleanedTitle)
        val rawPath = track.filePath
        if (!rawPath.isNullOrBlank()) {
            val oldFile = File(rawPath)
            if (oldFile.exists() && oldFile.canRead()) {
                val ext = oldFile.extension
                val newFileName = if (ext.isNotBlank()) "$cleanedTitle.$ext" else cleanedTitle
                val newFile = File(oldFile.parentFile, newFileName)
                if (newFile.absolutePath == oldFile.absolutePath) {
                    return@withContext track.copy(title = cleanedTitle)
                }
                val renamed = if (!newFile.exists()) runCatching { oldFile.renameTo(newFile) }.getOrDefault(false) else false
                if (renamed) {
                    val oldLrc = File(oldFile.parentFile, "${oldFile.nameWithoutExtension}.lrc")
                    if (oldLrc.exists()) {
                        runCatching { oldLrc.renameTo(File(oldFile.parentFile, "${newFile.nameWithoutExtension}.lrc")) }
                    }
                    runCatching {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(oldFile.absolutePath, newFile.absolutePath),
                            null,
                            null
                        )
                    }
                    updatedTrack = track.copy(
                        id = if (track.id.startsWith("fs:")) "fs:${newFile.absolutePath}" else track.id,
                        title = cleanedTitle,
                        uriString = Uri.fromFile(newFile).toString(),
                        filePath = newFile.absolutePath,
                        displayPath = newFile.absolutePath
                    )
                }
            }
        } else if (track.uriString.startsWith("content://")) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, cleanedTitle)
            }
            val rows = runCatching { context.contentResolver.update(Uri.parse(track.uriString), values, null, null) }.getOrDefault(0)
            if (rows <= 0) return@withContext null
        }

        _localTracks.update { list ->
            list.map { if (it.id == track.id) updatedTrack else it }
        }
        if (currentLocalTrack?.id == track.id) {
            currentLocalTrack = updatedTrack
            syncLocalPlatformSessionAndNotification()
        }
        rebuildSessionsSnapshot()
        updatedTrack
    }

    suspend fun deleteLocalTrack(track: LocalAudioTrack): Boolean = withContext(Dispatchers.IO) {
        if (currentLocalTrack?.id == track.id) {
            withContext(Dispatchers.Main.immediate) {
                releaseLocalPlayer()
                currentLocalTrack = null
                releaseLocalPlatformSession()
                abandonLocalAudioFocus()
            }
        }

        val deletedFile = !track.filePath.isNullOrBlank() && runCatching {
            File(track.filePath).delete()
        }.getOrDefault(false)

        val deletedUri = if (!deletedFile) {
            runCatching {
                context.contentResolver.delete(Uri.parse(track.uriString), null, null) > 0
            }.getOrDefault(false)
        } else {
            false
        }

        if (deletedFile || deletedUri) {
            _localTracks.update { list -> list.filterNot { it.id == track.id } }
            rebuildSessionsSnapshot()
            true
        } else {
            false
        }
    }

    fun registerDownloadedTrack(track: LocalAudioTrack) {
        _localTracks.update { current ->
            listOf(track) + current.filterNot {
                it.id == track.id || (!track.filePath.isNullOrBlank() && it.filePath == track.filePath)
            }
        }
        rebuildSessionsSnapshot()
    }

    fun refreshListenerStatusAndSessions() {
        val granted = isNotificationListenerGranted()
        refreshVolumeState()

        val mgr = mediaSessionManager
        if (!granted || mgr == null) {
            clearControllers()
            rebuildSessionsSnapshot()
            return
        }

        if (sessionsListener == null) {
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { newControllers ->
                bindControllers(newControllers.orEmpty())
            }
            sessionsListener = listener
            runCatching {
                mgr.addOnActiveSessionsChangedListener(listener, listenerComponent)
            }
        }

        runCatching {
            val active = mgr.getActiveSessions(listenerComponent)
            bindControllers(active)
        }.onFailure {
            _mediaState.update { it.copy(isListenerGranted = granted) }
        }
        if (activeBrowser == null) {
            val preferredPkg = resolveActiveController()?.packageName
                ?: if (resolveMediaBrowserComponent("ru.yandex.music") != null) "ru.yandex.music" else null
            connectMediaBrowserForActiveOrPackage(preferredPkg)
        }
    }

    fun isNotificationListenerGranted(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun openNotificationListenerSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun selectSession(sessionId: String) {
        _mediaState.update { state ->
            if (state.sessions.any { it.id == sessionId }) {
                state.copy(selectedSessionId = sessionId)
            } else {
                state
            }
        }
    }

    fun cycleNextSession(): Boolean = cycleSession(+1)

    fun cycleSession(direction: Int): Boolean {
        val sessions = _mediaState.value.sessions
        if (sessions.size <= 1) return false
        val currentId = _mediaState.value.activeSession?.id
        val idx = sessions.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        val step = if (direction >= 0) 1 else -1
        val nextIdx = ((idx + step) % sessions.size + sessions.size) % sessions.size
        selectSession(sessions[nextIdx].id)
        return true
    }

    fun togglePlayPause() {
        if (isLocalSessionActive()) {
            val mp = localPlayer
            if (mp != null && localPrepared) {
                runCatching {
                    localPositionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                    localPositionUpdatedAtMs = SystemClock.elapsedRealtime()
                    if (mp.isPlaying) {
                        mp.pause()
                        localIsPlaying = false
                        abandonLocalAudioFocus()
                    } else {
                        requestLocalAudioFocusAndPauseOthers()
                        mp.start()
                        localIsPlaying = true
                    }
                }
                syncLocalPlatformSessionAndNotification()
                rebuildSessionsSnapshot(forceSelectId = LOCAL_SESSION_ID)
                return
            }
        }
        val controller = resolveActiveController()
        if (controller != null) {
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) {
                MediaGuardRuntime.recordUserPause(controller.packageName)
                controller.transportControls.pause()
            } else {
                pauseLocalPlayerFromExternalSignal()
                MediaGuardRuntime.recordUserPlay(controller.packageName)
                controller.transportControls.play()
            }
        } else {
            val firstTrack = _localTracks.value.firstOrNull()
            if (firstTrack != null && currentLocalTrack == null) {
                playLocalTrack(firstTrack)
            } else {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
    }

    fun forceResumePackagePlayback(packageName: String): Boolean {
        var dispatched = false
        controllers.values
            .filter { it.packageName == packageName }
            .forEach { ctrl ->
                runCatching {
                    MediaGuardRuntime.recordUserPlay(packageName)
                    ctrl.transportControls.play()
                    dispatched = true
                }
            }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        return dispatched
    }

    fun skipNext() {
        if (isLocalSessionActive()) {
            val tracks = _localTracks.value
            val idx = tracks.indexOfFirst { it.id == currentLocalTrack?.id }
            if (tracks.isNotEmpty()) {
                val target = if (localShuffleEnabled && tracks.size > 1) {
                    tracks.filter { it.id != currentLocalTrack?.id }.randomOrNull() ?: tracks.first()
                } else {
                    val nextIdx = if (idx < 0) 0 else (idx + 1) % tracks.size
                    tracks[nextIdx]
                }
                playLocalTrack(target)
                return
            }
        }
        val controller = resolveActiveController()
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun skipPrevious() {
        if (isLocalSessionActive()) {
            val tracks = _localTracks.value
            val idx = tracks.indexOfFirst { it.id == currentLocalTrack?.id }
            if (tracks.isNotEmpty()) {
                val prevIdx = if (idx <= 0) tracks.lastIndex else idx - 1
                playLocalTrack(tracks[prevIdx])
                return
            }
        }
        val controller = resolveActiveController()
        if (controller != null) {
            controller.transportControls.skipToPrevious()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    fun stopPlayback() {
        if (isLocalSessionActive()) {
            releaseLocalPlayer()
            currentLocalTrack = null
            releaseLocalPlatformSession()
            abandonLocalAudioFocus()
            rebuildSessionsSnapshot()
            return
        }
        val controller = resolveActiveController()
        if (controller != null) {
            controller.transportControls.stop()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        if (isLocalSessionActive()) {
            val mp = localPlayer
            if (mp != null && localPrepared) {
                runCatching {
                    val target = if (localDurationMs > 0L) clamped.coerceIn(0L, localDurationMs) else clamped
                    mp.seekTo(target.toInt())
                    localPositionMs = target
                    localPositionUpdatedAtMs = SystemClock.elapsedRealtime()
                    syncLocalPlatformSessionAndNotification()
                    rebuildSessionsSnapshot(forceSelectId = LOCAL_SESSION_ID)
                }
                return
            }
        }
        val controller = resolveActiveController() ?: return
        controller.transportControls.seekTo(clamped)
    }

    fun skipToQueueItem(queueId: Long) {
        val controller = resolveActiveController() ?: return
        runCatching {
            controller.transportControls.skipToQueueItem(queueId)
        }
    }

    fun invokeCustomAction(action: String) {
        val controller = resolveActiveController() ?: return
        runCatching {
            controller.transportControls.sendCustomAction(action, null)
        }
    }

    fun triggerStreamingLike(packageName: String? = null): Boolean {
        val ctrl = resolveControllerForPackage(packageName) ?: return false
        val action = ctrl.playbackState?.customActions.orEmpty().firstOrNull { ca ->
            val hay = "${ca.action.orEmpty()} ${ca.name?.toString().orEmpty()}".lowercase()
            !hay.contains("dislike") && !hay.contains("unlike") && !hay.contains("не нрав") &&
                (hay.contains("like") || hay.contains("heart") || hay.contains("fav") ||
                    hay.contains("thumbs_up") || hay.contains("нравит") || hay.contains("лайк"))
        } ?: return false
        return runCatching {
            ctrl.transportControls.sendCustomAction(action.action, null)
            true
        }.getOrDefault(false)
    }

    fun triggerStreamingDislike(packageName: String? = null): Boolean {
        val ctrl = resolveControllerForPackage(packageName) ?: return false
        val action = ctrl.playbackState?.customActions.orEmpty().firstOrNull { ca ->
            val hay = "${ca.action.orEmpty()} ${ca.name?.toString().orEmpty()}".lowercase()
            hay.contains("dislike") || hay.contains("thumbs_down") || hay.contains("ban") ||
                hay.contains("не нрав") || hay.contains("дизлайк")
        }
        if (action != null) {
            return runCatching {
                ctrl.transportControls.sendCustomAction(action.action, null)
                true
            }.getOrDefault(false)
        }
        return false
    }

    fun triggerMyWave(packageName: String? = null): Boolean {
        val targetPkg = packageName?.takeIf { it.isNotBlank() }
            ?: resolveActiveController()?.packageName
        val ctrl = resolveControllerForPackage(targetPkg)
        if (ctrl != null) {
            val waveAction = ctrl.playbackState?.customActions.orEmpty().firstOrNull { ca ->
                val hay = "${ca.action.orEmpty()} ${ca.name?.toString().orEmpty()}".lowercase()
                hay.contains("wave") || hay.contains("vibe") || hay.contains("flow") ||
                    hay.contains("radio") || hay.contains("волн") || hay.contains("поток")
            }
            if (waveAction != null) {
                val sent = runCatching {
                    ctrl.transportControls.sendCustomAction(waveAction.action, null)
                    true
                }.getOrDefault(false)
                if (sent) return true
            }
        }

        val yandexInstalled = runCatching {
            context.packageManager.getLaunchIntentForPackage("ru.yandex.music") != null
        }.getOrDefault(false)

        if (targetPkg == "ru.yandex.music" || (targetPkg == null && yandexInstalled)) {
            val openedDeepLink = runCatching {
                val waveIntent = Intent(Intent.ACTION_VIEW, Uri.parse("yandexmusic://radio/user/onyourwave")).apply {
                    setPackage("ru.yandex.music")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(waveIntent)
                true
            }.getOrDefault(false)
            if (openedDeepLink) return true
        }

        if (ctrl != null) {
            val radioAction = ctrl.playbackState?.customActions.orEmpty().firstOrNull()
            if (radioAction != null) {
                val sent = runCatching {
                    ctrl.transportControls.sendCustomAction(radioAction.action, null)
                    true
                }.getOrDefault(false)
                if (sent) return true
            }
            return openActivePlayer()
        }
        return false
    }

    private fun resolveControllerForPackage(packageName: String?): MediaController? {
        if (!packageName.isNullOrBlank()) {
            controllers.values.firstOrNull { it.packageName == packageName }?.let { return it }
        }
        return resolveActiveController()
    }

    fun searchStreamingAndLocalCatalog(
        query: String,
        installedMusicApps: List<AppEntry>
    ): List<MusicStreamMatch> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val state = _mediaState.value
        val out = mutableListOf<MusicStreamMatch>()
        val seen = HashSet<String>()

        fun addMatch(item: MusicStreamMatch) {
            if (seen.add(item.id)) out.add(item)
        }

        // 1. Active queue & recent tracks across streaming sessions
        state.sessions.forEach { sess ->
            sess.queue.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.subtitle.contains(q, ignoreCase = true)
            }.take(8).forEach { qi ->
                addMatch(
                    MusicStreamMatch(
                        id = "q:${sess.packageName}:${qi.queueId}",
                        packageName = sess.packageName,
                        appLabel = sess.appLabel,
                        title = qi.title,
                        subtitle = qi.subtitle.ifBlank { sess.appLabel },
                        queueItemId = qi.queueId
                    )
                )
            }
        }

        // 2. Browsable streaming tracks
        state.browsableTracks.filter {
            it.isPlayable && (it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true))
        }.take(10).forEach { bt ->
            addMatch(
                MusicStreamMatch(
                    id = "mb:${bt.packageName}:${bt.mediaId}",
                    packageName = bt.packageName,
                    appLabel = bt.appLabel,
                    title = bt.title,
                    subtitle = bt.subtitle.ifBlank { bt.appLabel },
                    mediaId = bt.mediaId
                )
            )
        }

        // 3. Recent streaming / TG session tracks
        state.recentTracks.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.subtitle.contains(q, ignoreCase = true)
        }.take(6).forEach { rt ->
            addMatch(
                MusicStreamMatch(
                    id = "rec:${rt.packageName}:${rt.title}",
                    packageName = rt.packageName,
                    appLabel = rt.appLabel,
                    title = rt.title,
                    subtitle = rt.subtitle.ifBlank { rt.appLabel }
                )
            )
        }

        // 4. Local + Telegram downloaded audio files
        _localTracks.value.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }.take(12).forEach { lt ->
            addMatch(
                MusicStreamMatch(
                    id = "loc:${lt.id}",
                    packageName = context.packageName,
                    appLabel = if (lt.isTelegramAudio) "Telegram Audio" else "Файлы",
                    title = lt.title,
                    subtitle = lt.artist.ifBlank { lt.displayPath },
                    isLocalTrack = true,
                    localTrack = lt
                )
            )
        }

        // 5. Cross-streaming live search triggers for installed streaming services (Yandex Music prioritized first)
        val streamingApps = installedMusicApps
            .filter { isStreamingPackage(it.packageName) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<AppEntry> { sortPriorityScore(it.packageName) }.thenBy { it.label.lowercase() })
            .take(6)

        streamingApps.forEach { app ->
            addMatch(
                MusicStreamMatch(
                    id = "stream_search:${app.packageName}:$q",
                    packageName = app.packageName,
                    appLabel = app.label,
                    title = "Найти «$q»",
                    subtitle = app.label,
                    isServiceSearchTrigger = true
                )
            )
        }

        return out
    }

    fun playFromSearchInStream(query: String, packageName: String? = null): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false

        val controller = if (!packageName.isNullOrBlank()) {
            controllers.values.firstOrNull { it.packageName == packageName } ?: resolveActiveController()
        } else {
            resolveActiveController()
        }

        if (controller != null) {
            runCatching {
                controller.transportControls.playFromSearch(trimmed, Bundle.EMPTY)
                return true
            }
        }
        return false
    }

    fun playBrowserTrack(track: MediaBrowserTrack): Boolean {
        val matchingController = controllers.values.firstOrNull { it.packageName == track.packageName }
        if (matchingController != null) {
            runCatching {
                matchingController.transportControls.playFromMediaId(track.mediaId, null)
                return true
            }
        }
        val browser = activeBrowser
        if (browser != null && browser.isConnected && activeBrowserPackage == track.packageName) {
            val token = runCatching { browser.sessionToken }.getOrNull()
            if (token != null) {
                runCatching {
                    val tempController = MediaController(context, token)
                    tempController.transportControls.playFromMediaId(track.mediaId, null)
                    return true
                }
            }
        }
        return playFromSearchInStream(track.title, track.packageName)
    }

    fun connectMediaBrowserForActiveOrPackage(packageName: String?) {
        val targetPkg = packageName?.takeIf { it.isNotBlank() && it != context.packageName }
            ?: resolveActiveController()?.packageName
            ?: discoverFirstBrowsablePackage()
            ?: return

        if (activeBrowserPackage == targetPkg && activeBrowser?.isConnected == true) {
            return
        }
        disconnectMediaBrowser()

        val component = resolveMediaBrowserComponent(targetPkg) ?: return
        activeBrowserPackage = targetPkg
        val appLabel = resolveAppLabel(targetPkg)

        runCatching {
            val browser = MediaBrowser(
                context,
                component,
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        val b = activeBrowser ?: return
                        val root = runCatching { b.root }.getOrNull() ?: return
                        _mediaState.update { it.copy(connectedBrowserPackage = targetPkg) }
                        runCatching {
                            b.subscribe(root, object : MediaBrowser.SubscriptionCallback() {
                                override fun onChildrenLoaded(
                                    parentId: String,
                                    children: MutableList<MediaBrowser.MediaItem>
                                ) {
                                    val collected = mutableListOf<MediaBrowserTrack>()
                                    val subFolders = mutableListOf<String>()
                                    children.take(30).forEach { item ->
                                        val desc = item.description
                                        val title = desc.title?.toString()?.trim().orEmpty()
                                        val subtitle = desc.subtitle?.toString()?.trim().orEmpty()
                                        if (title.isNotEmpty()) {
                                            collected.add(
                                                MediaBrowserTrack(
                                                    mediaId = item.mediaId ?: title,
                                                    packageName = targetPkg,
                                                    appLabel = appLabel,
                                                    title = title,
                                                    subtitle = subtitle,
                                                    isPlayable = item.isPlayable,
                                                    isBrowsable = item.isBrowsable
                                                )
                                            )
                                        }
                                        if (item.isBrowsable && subFolders.size < 3 && !item.mediaId.isNullOrBlank()) {
                                            subFolders.add(item.mediaId!!)
                                        }
                                    }
                                    _mediaState.update { state ->
                                        val merged = (collected + state.browsableTracks)
                                            .distinctBy { "${it.packageName}:${it.mediaId}" }
                                            .take(80)
                                        state.copy(browsableTracks = merged)
                                    }
                                    subFolders.forEach { folderId ->
                                        runCatching {
                                            b.subscribe(folderId, object : MediaBrowser.SubscriptionCallback() {
                                                override fun onChildrenLoaded(
                                                    subParent: String,
                                                    subChildren: MutableList<MediaBrowser.MediaItem>
                                                ) {
                                                    val subTracks = subChildren.take(25).mapNotNull { sub ->
                                                        val t = sub.description.title?.toString()?.trim().orEmpty()
                                                        if (t.isEmpty()) return@mapNotNull null
                                                        MediaBrowserTrack(
                                                            mediaId = sub.mediaId ?: t,
                                                            packageName = targetPkg,
                                                            appLabel = appLabel,
                                                            title = t,
                                                            subtitle = sub.description.subtitle?.toString()?.trim().orEmpty(),
                                                            isPlayable = sub.isPlayable,
                                                            isBrowsable = sub.isBrowsable
                                                        )
                                                    }
                                                    _mediaState.update { state ->
                                                        val merged = (state.browsableTracks + subTracks)
                                                            .distinctBy { "${it.packageName}:${it.mediaId}" }
                                                            .take(120)
                                                        state.copy(browsableTracks = merged)
                                                    }
                                                }
                                            })
                                        }
                                    }
                                }
                            })
                        }
                    }

                    override fun onConnectionSuspended() {
                        _mediaState.update { it.copy(connectedBrowserPackage = null) }
                    }

                    override fun onConnectionFailed() {
                        _mediaState.update { it.copy(connectedBrowserPackage = null) }
                    }
                },
                null
            )
            activeBrowser = browser
            browser.connect()
        }
    }

    fun searchConnectedMediaBrowser(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _mediaState.update { it.copy(browserSearchResults = emptyList(), isSearchingStream = false) }
            return
        }
        val state = _mediaState.value
        val browser = activeBrowser
        val pkg = activeBrowserPackage

        // Subscribe to any browsable folders whose title matches the query to surface deeper items
        if (browser != null && browser.isConnected && pkg != null) {
            val appLabel = resolveAppLabel(pkg)
            state.browsableTracks
                .filter { it.isBrowsable && it.title.contains(trimmed, ignoreCase = true) }
                .take(3)
                .forEach { folder ->
                    runCatching {
                        browser.subscribe(folder.mediaId, object : MediaBrowser.SubscriptionCallback() {
                            override fun onChildrenLoaded(
                                parentId: String,
                                children: MutableList<MediaBrowser.MediaItem>
                            ) {
                                val mapped = children.take(30).mapNotNull { item ->
                                    val t = item.description.title?.toString()?.trim().orEmpty()
                                    if (t.isEmpty()) return@mapNotNull null
                                    MediaBrowserTrack(
                                        mediaId = item.mediaId ?: t,
                                        packageName = pkg,
                                        appLabel = appLabel,
                                        title = t,
                                        subtitle = item.description.subtitle?.toString()?.trim().orEmpty(),
                                        isPlayable = item.isPlayable,
                                        isBrowsable = item.isBrowsable
                                    )
                                }
                                _mediaState.update { cur ->
                                    val mergedCatalog = (cur.browsableTracks + mapped)
                                        .distinctBy { "${it.packageName}:${it.mediaId}" }
                                        .take(150)
                                    val filtered = mergedCatalog.filter {
                                        it.title.contains(trimmed, ignoreCase = true) ||
                                            it.subtitle.contains(trimmed, ignoreCase = true)
                                    }
                                    cur.copy(
                                        browsableTracks = mergedCatalog,
                                        browserSearchResults = filtered,
                                        isSearchingStream = false
                                    )
                                }
                            }
                        })
                    }
                }
        }

        val matches = state.browsableTracks.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.subtitle.contains(trimmed, ignoreCase = true)
        }
        _mediaState.update {
            it.copy(
                browserSearchResults = matches,
                isSearchingStream = false
            )
        }
    }

    private fun disconnectMediaBrowser() {
        runCatching { activeBrowser?.disconnect() }
        activeBrowser = null
        activeBrowserPackage = null
        _mediaState.update {
            it.copy(
                connectedBrowserPackage = null,
                browsableTracks = emptyList(),
                browserSearchResults = emptyList(),
                isSearchingStream = false
            )
        }
    }

    private fun resolveMediaBrowserComponent(packageName: String): ComponentName? {
        val pm = context.packageManager
        val intent = Intent("android.media.browse.MediaBrowserService").setPackage(packageName)
        val services = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(intent, 0)
            }
        }.getOrDefault(emptyList())
        val svcInfo = services.firstOrNull()?.serviceInfo ?: return null
        return ComponentName(svcInfo.packageName, svcInfo.name)
    }

    private fun discoverFirstBrowsablePackage(): String? {
        val pm = context.packageManager
        val intent = Intent("android.media.browse.MediaBrowserService")
        val services = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(intent, 0)
            }
        }.getOrDefault(emptyList())
        return services
            .mapNotNull { it.serviceInfo?.packageName }
            .sortedBy { sortPriorityScore(it) }
            .firstOrNull()
    }

    private fun resolveAppLabel(pkg: String): String {
        return appLabelCache.getOrPut(pkg) {
            runCatching {
                val ai = context.packageManager.getApplicationInfo(pkg, 0)
                context.packageManager.getApplicationLabel(ai).toString()
            }.getOrDefault(pkg)
        }
    }

    fun seekBy(deltaMs: Long) {
        val session = _mediaState.value.activeSession ?: return
        val current = currentEstimatedPositionMs(session)
        val maxDur = if (session.durationMs > 0) session.durationMs else Long.MAX_VALUE
        val target = (current + deltaMs).coerceIn(0L, maxDur)
        seekTo(target)
    }

    fun openActivePlayer(): Boolean {
        val controller = resolveActiveController() ?: return false
        val sessionIntent = controller.sessionActivity
        if (sessionIntent != null) {
            val sent = runCatching {
                sessionIntent.send()
                true
            }.getOrDefault(false)
            if (sent) return true
        }
        return runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(controller.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launch != null) {
                context.startActivity(launch)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun adjustVolume(delta: Int) {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (current + delta).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        refreshVolumeState()
    }

    fun setVolume(volume: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = volume.coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        refreshVolumeState()
    }

    fun toggleMute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
            val direction = if (isMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        } else {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (current > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            }
        }
        refreshVolumeState()
    }

    fun refreshVolumeState() {
        val current = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val max = runCatching { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15)
        val muted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { audioManager.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(current == 0)
        } else {
            current == 0
        }
        _mediaState.update {
            it.copy(systemVolume = current, maxVolume = max.coerceAtLeast(1), isMuted = muted)
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun resolveActiveController(): MediaController? {
        val selectedId = _mediaState.value.selectedSessionId
        if (selectedId != null && selectedId != LOCAL_SESSION_ID && controllers.containsKey(selectedId)) {
            return controllers[selectedId]
        }
        val sorted = controllers.values.sortedWith(
            compareByDescending<MediaController> {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            }.thenBy { sortPriorityScore(it.packageName) }
        )
        return sorted.firstOrNull()
    }

    private fun bindControllers(newList: List<MediaController>) {
        clearControllers()
        newList.forEach { controller ->
            if (controller.packageName == context.packageName) return@forEach
            val id = controllerId(controller)
            controllers[id] = controller
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    state?.let {
                        MediaGuardRuntime.onSessionPlaybackState(controller.packageName, it.state)
                        if (it.state == PlaybackState.STATE_PLAYING && localIsPlaying) {
                            pauseLocalPlayerFromExternalSignal()
                        }
                    }
                    rebuildSessionsSnapshot()
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    rebuildSessionsSnapshot()
                }

                override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
                    rebuildSessionsSnapshot()
                }

                override fun onSessionDestroyed() {
                    controllers.remove(id)
                    controllerCallbacks.remove(id)
                    rebuildSessionsSnapshot()
                }
            }
            controllerCallbacks[id] = callback
            runCatching { controller.registerCallback(callback) }
        }
        rebuildSessionsSnapshot()
        resolveActiveController()?.packageName?.let { connectMediaBrowserForActiveOrPackage(it) }
    }

    private fun clearControllers() {
        controllerCallbacks.forEach { (id, cb) ->
            controllers[id]?.let { ctrl ->
                runCatching { ctrl.unregisterCallback(cb) }
            }
        }
        controllerCallbacks.clear()
        controllers.clear()
    }

    private fun rebuildSessionsSnapshot(forceSelectId: String? = null) {
        val list = mutableListOf<MediaSessionUi>()
        controllers.entries.forEach { (id, ctrl) ->
            val model = toUiModel(id, ctrl)
            list.add(model)
            // Keep rolling history of real streaming/TG tracks played outside local player
            if (model.title.isNotBlank() && model.title != model.appLabel) {
                val hKey = "${model.packageName}|${model.title}|${model.artist}"
                recentTrackHistory.remove(hKey)
                recentTrackHistory[hKey] = MediaQueueTrack(
                    queueId = hKey.hashCode().toLong(),
                    title = model.title,
                    subtitle = model.artist.ifBlank { model.appLabel },
                    packageName = model.packageName,
                    appLabel = model.appLabel,
                    isActive = model.isPlaying
                )
                while (recentTrackHistory.size > 40) {
                    val firstKey = recentTrackHistory.keys.firstOrNull() ?: break
                    recentTrackHistory.remove(firstKey)
                }
            }
        }
        currentLocalTrack?.let { local ->
            val currentPos = if (localPrepared && localPlayer != null) {
                runCatching { localPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L) }.getOrNull() ?: localPositionMs
            } else {
                localPositionMs
            }
            list.add(
                MediaSessionUi(
                    id = LOCAL_SESSION_ID,
                    packageName = context.packageName,
                    appLabel = "KeyDeck Poweramp",
                    title = local.title,
                    artist = local.artist,
                    album = local.album,
                    isPlaying = localIsPlaying,
                    positionMs = currentPos,
                    durationMs = localDurationMs.coerceAtLeast(local.durationMs),
                    playbackSpeed = localPlaybackSpeed,
                    lastUpdateElapsedMs = SystemClock.elapsedRealtime(),
                    artwork = null,
                    canSkipNext = true,
                    canSkipPrev = true,
                    canSeek = localDurationMs > 0L,
                    formatLabel = local.formatLabel.ifBlank {
                        local.filePath?.substringAfterLast('.', "")?.uppercase().orEmpty()
                    },
                    bitrateKbps = local.bitrateKbps,
                    sampleRateHz = local.sampleRateHz
                )
            )
        }

        val sessions = list.sortedWith(
            compareByDescending<MediaSessionUi> { it.isPlaying }
                .thenBy { sortPriorityScore(it.packageName) }
                .thenBy { it.appLabel.lowercase() }
        )

        val recentReversed = recentTrackHistory.values.toList().reversed()
        onTrackHistoryUpdated?.invoke(recentReversed)

        _mediaState.update { state ->
            val nextSelected = selectPreferredSessionId(
                sessions = sessions,
                currentSelectedId = state.selectedSessionId,
                forceSelectId = forceSelectId
            )

            state.copy(
                isListenerGranted = isNotificationListenerGranted(),
                sessions = sessions,
                selectedSessionId = nextSelected,
                currentLocalTrackId = currentLocalTrack?.id,
                recentTracks = recentReversed
            )
        }
    }

    private fun toUiModel(id: String, controller: MediaController): MediaSessionUi {
        val metadata = controller.metadata
        val playback = controller.playbackState
        val pkg = controller.packageName
        val appLabel = resolveAppLabel(pkg)
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: appLabel
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.trim()
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.trim()
            ?: ""
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val pos = playback?.position?.coerceAtLeast(0L) ?: 0L
        val speed = playback?.playbackSpeed ?: 1f
        val updateTime = playback?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING
        val actions = playback?.actions ?: 0L
        val activeQueueId = playback?.activeQueueItemId ?: MediaSession.QueueItem.UNKNOWN_ID.toLong()

        val queueItems = runCatching {
            controller.queue.orEmpty().mapNotNull { qi ->
                val desc = qi.description
                val qTitle = desc.title?.toString()?.trim().orEmpty()
                if (qTitle.isEmpty()) return@mapNotNull null
                val qSub = desc.subtitle?.toString()?.trim()
                    ?: desc.description?.toString()?.trim()
                    ?: ""
                MediaQueueTrack(
                    queueId = qi.queueId,
                    title = qTitle,
                    subtitle = qSub,
                    mediaId = desc.mediaId,
                    packageName = pkg,
                    appLabel = appLabel,
                    isActive = qi.queueId == activeQueueId
                )
            }
        }.getOrDefault(emptyList())

        val customActions = runCatching {
            playback?.customActions.orEmpty().map { ca ->
                MediaCustomActionItem(
                    action = ca.action.orEmpty(),
                    name = ca.name?.toString()?.trim().orEmpty().ifBlank { ca.action.orEmpty() }
                )
            }.filter { it.action.isNotBlank() }
        }.getOrDefault(emptyList())

        val artwork = extractArtwork(metadata)

        return MediaSessionUi(
            id = id,
            packageName = pkg,
            appLabel = appLabel,
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            positionMs = pos,
            durationMs = duration,
            playbackSpeed = speed,
            lastUpdateElapsedMs = updateTime,
            artwork = artwork,
            canSkipNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L || actions == 0L,
            canSkipPrev = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L || actions == 0L,
            canSeek = duration > 0L || (actions and PlaybackState.ACTION_SEEK_TO) != 0L,
            canPlayFromSearch = (actions and PlaybackState.ACTION_PLAY_FROM_SEARCH) != 0L || actions == 0L,
            activeQueueItemId = activeQueueId,
            queue = queueItems,
            customActions = customActions,
            formatLabel = if (isStreamingPackage(pkg)) "STREAM" else "MEDIA"
        )
    }

    private fun extractArtwork(metadata: MediaMetadata?): ImageBitmap? {
        if (metadata == null) return null
        val directBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        if (directBitmap != null) {
            return scaleBitmap(directBitmap).asImageBitmap()
        }

        val uriStr = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
        if (!uriStr.isNullOrBlank()) {
            val loaded = runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
            if (loaded != null) {
                return scaleBitmap(loaded).asImageBitmap()
            }
        }
        return null
    }

    private fun scaleBitmap(bmp: Bitmap): Bitmap {
        val maxDim = 256
        if (bmp.width <= maxDim && bmp.height <= maxDim) return bmp
        val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    private fun controllerId(controller: MediaController): String {
        return "${controller.packageName}:${controller.sessionToken.hashCode()}"
    }

    private fun readInitialState(): MediaPlayerState {
        val current = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val max = runCatching { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15)
        return MediaPlayerState(
            isListenerGranted = isNotificationListenerGranted(),
            systemVolume = current,
            maxVolume = max.coerceAtLeast(1),
            isMuted = current == 0
        )
    }

    companion object {
        const val LOCAL_SESSION_ID = "keydeck:local_player"
        private const val LOCAL_MEDIA_CHANNEL_ID = "keydeck_local_player_media"
        private const val LOCAL_MEDIA_NOTIF_ID = 4792

        fun selectPreferredSessionId(
            sessions: List<MediaSessionUi>,
            currentSelectedId: String?,
            forceSelectId: String? = null
        ): String? {
            if (sessions.isEmpty()) return null
            if (forceSelectId != null && sessions.any { it.id == forceSelectId }) {
                return forceSelectId
            }
            val current = sessions.firstOrNull { it.id == currentSelectedId }
            val anyPlaying = sessions.any { it.isPlaying }
            if (current != null && (current.isPlaying || !anyPlaying)) {
                return current.id
            }
            return sessions.firstOrNull { it.isPlaying }?.id ?: sessions.firstOrNull()?.id
        }

        fun sortPriorityScore(packageName: String): Int {
            val pkg = packageName.lowercase()
            return when {
                pkg == "ru.yandex.music" || pkg.contains("yandex.music") -> 0
                pkg.contains("shazam") || pkg.contains("vk") -> 2
                else -> 1
            }
        }

        fun isStreamingPackage(packageName: String): Boolean {
            val pkg = packageName.lowercase()
            if (pkg.isBlank()) return false
            val offlineLocalPlayers = listOf(
                "aimp", "poweramp", "neutron", "foobar", "vlc", "musicolet",
                "pulsar", "oto", "retro.music", "phonograph", "vanilla", "blackplayer"
            )
            if (offlineLocalPlayers.any { pkg.contains(it) }) return false
            return pkg.contains("yandex.music") ||
                pkg.contains("spotify") ||
                pkg.contains("youtube") ||
                pkg.contains("revanced") ||
                pkg.contains("vkmusic") ||
                pkg.contains("vkontakte") ||
                pkg.contains("zvuk") ||
                pkg.contains("sber") ||
                pkg.contains("apple.android.music") ||
                pkg.contains("soundcloud") ||
                pkg.contains("deezer") ||
                pkg.contains("tidal") ||
                pkg.contains("mts") ||
                pkg.contains("shazam") ||
                pkg.contains("telegram") ||
                pkg.contains("exteragram") ||
                pkg.contains("nekogram") ||
                pkg.contains("ayugram")
        }

        fun currentEstimatedPositionMs(session: MediaSessionUi): Long {
            if (!session.isPlaying) return session.positionMs
            val elapsed = (SystemClock.elapsedRealtime() - session.lastUpdateElapsedMs).coerceAtLeast(0L)
            val est = session.positionMs + (elapsed * session.playbackSpeed).toLong()
            return if (session.durationMs > 0L) est.coerceIn(0L, session.durationMs) else est.coerceAtLeast(0L)
        }

        fun formatTimeMs(ms: Long): String {
            val totalSec = (ms / 1000L).coerceAtLeast(0L)
            val minutes = totalSec / 60L
            val seconds = totalSec % 60L
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
