package com.example.data

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
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
import androidx.core.app.NotificationManagerCompat
import com.example.model.LocalAudioTrack
import com.example.model.MediaBrowserTrack
import com.example.model.MediaCustomActionItem
import com.example.model.MediaPlayerState
import com.example.model.MediaQueueTrack
import com.example.model.MediaSessionUi
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

    private var activeBrowser: MediaBrowser? = null
    private var activeBrowserPackage: String? = null
    private val recentTrackHistory = LinkedHashMap<String, MediaQueueTrack>()

    fun startObserving() {
        refreshListenerStatusAndSessions()
    }

    fun stopObserving() {
        clearControllers()
        disconnectMediaBrowser()
        releaseLocalPlayer()
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
                            isTelegramAudio = isTgMediaStore
                        )
                    )
                }
            }
        }

        // 2. Supplement: Telegram Audio & Music/Download folder scan
        if (UniversalSearchRepository.hasAllFilesAccess(context)) {
            val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            val dirs = listOfNotNull(
                root?.let { File(it, "Telegram/Telegram Audio") },
                root?.let { File(it, "Telegram/Telegram Documents") },
                root?.let { File(it, "Music/Telegram") },
                root?.let { File(it, "Download/Telegram") },
                root?.let { File(it, "Android/media/org.telegram.messenger/Telegram/Telegram Audio") },
                root?.let { File(it, "Android/media/com.exteragram.messenger/Telegram/Telegram Audio") },
                root?.let { File(it, "Android/media/tw.nekomimi.nekogram/Telegram/Telegram Audio") },
                root?.let { File(it, "Android/media/com.radolyn.ayugram/Telegram/Telegram Audio") },
                root?.let { File(it, "Music") },
                root?.let { File(it, "Download") },
                root?.let { File(it, "Audio") },
                File(context.filesDir, "music")
            ).filter { it.exists() && it.isDirectory }

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
                                        isTelegramAudio = isTgFile
                                    )
                                )
                            } else if (isTgFile && !existingEntry.value.isTelegramAudio) {
                                found[existingEntry.key] = existingEntry.value.copy(isTelegramAudio = true)
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

    private fun readAudioMetaFast(file: File): Triple<String, String, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Triple(title, artist, dur)
        } catch (_: Throwable) {
            Triple("", "", 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun playLocalTrack(track: LocalAudioTrack) {
        runCatching {
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
                    mp.start()
                    rebuildSessionsSnapshot(forceSelectId = LOCAL_SESSION_ID)
                }
                setOnCompletionListener {
                    localIsPlaying = false
                    localPositionMs = localDurationMs
                    localPositionUpdatedAtMs = SystemClock.elapsedRealtime()
                    val tracks = _localTracks.value
                    val currentIdx = tracks.indexOfFirst { it.id == track.id }
                    if (currentIdx >= 0 && currentIdx + 1 < tracks.size) {
                        playLocalTrack(tracks[currentIdx + 1])
                    } else {
                        rebuildSessionsSnapshot()
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
            rebuildSessionsSnapshot(forceSelectId = LOCAL_SESSION_ID)
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
                    } else {
                        mp.start()
                        localIsPlaying = true
                    }
                }
                rebuildSessionsSnapshot(forceSelectId = LOCAL_SESSION_ID)
                return
            }
        }
        val controller = resolveActiveController()
        if (controller != null) {
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) {
                controller.transportControls.pause()
            } else {
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

    fun skipNext() {
        if (isLocalSessionActive()) {
            val tracks = _localTracks.value
            val idx = tracks.indexOfFirst { it.id == currentLocalTrack?.id }
            if (tracks.isNotEmpty()) {
                val nextIdx = if (idx < 0) 0 else (idx + 1) % tracks.size
                playLocalTrack(tracks[nextIdx])
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
        return services.firstOrNull()?.serviceInfo?.packageName
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
        val playing = controllers.values.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        return playing ?: controllers.values.firstOrNull()
    }

    private fun bindControllers(newList: List<MediaController>) {
        clearControllers()
        newList.forEach { controller ->
            val id = controllerId(controller)
            controllers[id] = controller
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
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
                    appLabel = "KeyDeck Плеер",
                    title = local.title,
                    artist = local.artist,
                    album = local.album,
                    isPlaying = localIsPlaying,
                    positionMs = currentPos,
                    durationMs = localDurationMs.coerceAtLeast(local.durationMs),
                    playbackSpeed = 1f,
                    lastUpdateElapsedMs = SystemClock.elapsedRealtime(),
                    artwork = null,
                    canSkipNext = true,
                    canSkipPrev = true,
                    canSeek = localDurationMs > 0L
                )
            )
        }

        val sessions = list.sortedWith(
            compareByDescending<MediaSessionUi> { it.isPlaying }
                .thenBy { it.appLabel.lowercase() }
        )

        val recentReversed = recentTrackHistory.values.toList().reversed()

        _mediaState.update { state ->
            val stillValidId = forceSelectId?.takeIf { sel -> sessions.any { it.id == sel } }
                ?: state.selectedSessionId?.takeIf { sel -> sessions.any { it.id == sel } }
            val nextSelected = stillValidId
                ?: sessions.firstOrNull { it.isPlaying }?.id
                ?: sessions.firstOrNull()?.id

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
            customActions = customActions
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
