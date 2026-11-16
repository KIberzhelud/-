package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import com.example.model.MediaPlayerState
import com.example.model.MediaSessionUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    fun startObserving() {
        refreshListenerStatusAndSessions()
    }

    fun stopObserving() {
        clearControllers()
        sessionsListener?.let { listener ->
            runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(listener) }
        }
        sessionsListener = null
    }

    fun refreshListenerStatusAndSessions() {
        val granted = isNotificationListenerGranted()
        refreshVolumeState()

        val mgr = mediaSessionManager
        if (!granted || mgr == null) {
            clearControllers()
            _mediaState.update {
                it.copy(
                    isListenerGranted = granted,
                    sessions = emptyList(),
                    selectedSessionId = null
                )
            }
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
        val controller = resolveActiveController()
        if (controller != null) {
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun skipNext() {
        val controller = resolveActiveController()
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun skipPrevious() {
        val controller = resolveActiveController()
        if (controller != null) {
            controller.transportControls.skipToPrevious()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    fun stopPlayback() {
        val controller = resolveActiveController()
        if (controller != null) {
            controller.transportControls.stop()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = resolveActiveController() ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        controller.transportControls.seekTo(clamped)
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
        if (selectedId != null && controllers.containsKey(selectedId)) {
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

    private fun rebuildSessionsSnapshot() {
        val sessions = controllers.entries.map { (id, ctrl) ->
            toUiModel(id, ctrl)
        }.sortedWith(
            compareByDescending<MediaSessionUi> { it.isPlaying }
                .thenBy { it.appLabel.lowercase() }
        )

        _mediaState.update { state ->
            val stillValidId = state.selectedSessionId?.takeIf { sel ->
                sessions.any { it.id == sel }
            }
            val nextSelected = stillValidId
                ?: sessions.firstOrNull { it.isPlaying }?.id
                ?: sessions.firstOrNull()?.id

            state.copy(
                isListenerGranted = isNotificationListenerGranted(),
                sessions = sessions,
                selectedSessionId = nextSelected
            )
        }
    }

    private fun toUiModel(id: String, controller: MediaController): MediaSessionUi {
        val metadata = controller.metadata
        val playback = controller.playbackState
        val pkg = controller.packageName
        val appLabel = appLabelCache.getOrPut(pkg) {
            runCatching {
                val ai = context.packageManager.getApplicationInfo(pkg, 0)
                context.packageManager.getApplicationLabel(ai).toString()
            }.getOrDefault(pkg)
        }
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
            canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L && duration > 0L
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
