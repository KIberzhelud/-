package com.example.data

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaScannerConnection
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AudioCaptureState
import com.example.model.EqualizerBandUi
import com.example.model.EqualizerPreset
import com.example.model.EqualizerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

class AudioDspController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var attachedSessionId: Int = 0

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attachAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (attachedSessionId == sessionId && equalizer != null) {
            applyCurrentSettingsToEffects()
            return
        }
        releaseEffects()
        attachedSessionId = sessionId

        var hardwareReady = false
        runCatching {
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            val bandCount = eq.numberOfBands.toInt().coerceIn(0, 8)
            val range = eq.bandLevelRange
            val minMb = range?.getOrNull(0)?.toInt() ?: -1500
            val maxMb = range?.getOrNull(1)?.toInt() ?: 1500
            val prevBands = _state.value.bands
            val mappedBands = if (bandCount > 0) {
                (0 until bandCount).map { idx ->
                    val hz = runCatching { eq.getCenterFreq(idx.toShort()) / 1000 }.getOrDefault(1000)
                    val label = if (hz >= 1000) {
                        val khz = hz / 1000f
                        if (khz == khz.toInt().toFloat()) "${khz.toInt()}kHz" else "%.1fkHz".format(khz)
                    } else {
                        "${hz}Hz"
                    }
                    val savedLevel = prevBands.getOrNull(idx)?.levelMb ?: 0
                    EqualizerBandUi(
                        index = idx,
                        freqLabel = label,
                        levelMb = savedLevel.coerceIn(minMb, maxMb)
                    )
                }
            } else {
                prevBands
            }
            _state.update { it.copy(bands = mappedBands, dspHardwareAttached = true) }
            hardwareReady = true
        }

        runCatching {
            bassBoost = BassBoost(0, sessionId)
        }
        runCatching {
            virtualizer = Virtualizer(0, sessionId)
        }

        if (!hardwareReady) {
            _state.update { it.copy(dspHardwareAttached = false) }
        }
        applyCurrentSettingsToEffects()
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        applyCurrentSettingsToEffects()
    }

    fun applyPreset(preset: EqualizerPreset) {
        _state.update { cur ->
            val updatedBands = cur.bands.mapIndexed { idx, band ->
                band.copy(levelMb = preset.bandLevelsMb.getOrElse(idx) { 0 }.coerceIn(-1500, 1500))
            }
            cur.copy(
                preset = preset,
                bands = updatedBands,
                bassBoostStrength = preset.bassBoost,
                virtualizerStrength = preset.virtualizer,
                enabled = true
            )
        }
        applyCurrentSettingsToEffects()
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val clamped = levelMb.coerceIn(-1500, 1500)
        _state.update { cur ->
            cur.copy(
                bands = cur.bands.map { if (it.index == bandIndex) it.copy(levelMb = clamped) else it }
            )
        }
        applyCurrentSettingsToEffects()
    }

    fun setBassBoost(strength: Int) {
        _state.update { it.copy(bassBoostStrength = strength.coerceIn(0, 1000)) }
        applyCurrentSettingsToEffects()
    }

    fun setVirtualizer(strength: Int) {
        _state.update { it.copy(virtualizerStrength = strength.coerceIn(0, 1000)) }
        applyCurrentSettingsToEffects()
    }

    fun setStateMeta(speed: Float? = null, repeatMode: com.example.model.RepeatMode? = null, shuffle: Boolean? = null) {
        _state.update { cur ->
            cur.copy(
                playbackSpeed = speed?.coerceIn(0.75f, 1.5f) ?: cur.playbackSpeed,
                repeatMode = repeatMode ?: cur.repeatMode,
                shuffleEnabled = shuffle ?: cur.shuffleEnabled
            )
        }
    }

    fun applySaved(enabled: Boolean, preset: EqualizerPreset, bandsMb: List<Int>, bass: Int, virt: Int) {
        _state.update { cur ->
            val currentSize = cur.bands.size.coerceAtLeast(5)
            val newBands = (0 until currentSize).map { idx ->
                val existing = cur.bands.getOrNull(idx)
                EqualizerBandUi(
                    index = idx,
                    freqLabel = existing?.freqLabel ?: EqualizerState.defaultBands().getOrElse(idx) {
                        EqualizerBandUi(idx, "${idx + 1}", 0)
                    }.freqLabel,
                    levelMb = bandsMb.getOrElse(idx) { existing?.levelMb ?: 0 }.coerceIn(-1500, 1500)
                )
            }
            cur.copy(
                enabled = enabled,
                preset = preset,
                bands = newBands,
                bassBoostStrength = bass.coerceIn(0, 1000),
                virtualizerStrength = virt.coerceIn(0, 1000)
            )
        }
        applyCurrentSettingsToEffects()
    }

    private fun applyCurrentSettingsToEffects() {
        val cur = _state.value
        runCatching {
            equalizer?.let { eq ->
                eq.enabled = cur.enabled
                val range = eq.bandLevelRange
                val minMb = range?.getOrNull(0)?.toInt() ?: -1500
                val maxMb = range?.getOrNull(1)?.toInt() ?: 1500
                val hwBands = eq.numberOfBands.toInt()
                cur.bands.take(hwBands).forEach { band ->
                    eq.setBandLevel(
                        band.index.toShort(),
                        band.levelMb.coerceIn(minMb, maxMb).toShort()
                    )
                }
            }
        }
        runCatching {
            bassBoost?.let { bb ->
                bb.enabled = cur.enabled && cur.bassBoostStrength > 0
                if (bb.strengthSupported) {
                    bb.setStrength(cur.bassBoostStrength.toShort())
                }
            }
        }
        runCatching {
            virtualizer?.let { vr ->
                vr.enabled = cur.enabled && cur.virtualizerStrength > 0
                if (vr.strengthSupported) {
                    vr.setStrength(cur.virtualizerStrength.toShort())
                }
            }
        }
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        attachedSessionId = 0
        _state.update { it.copy(dspHardwareAttached = false) }
    }
}

data class CompletedAudioCapture(
    val filePath: String,
    val title: String,
    val artist: String,
    val targetAlbumId: String?
)

object StreamAudioCaptureRuntime {
    private val _captureState = MutableStateFlow(AudioCaptureState())
    val captureState: StateFlow<AudioCaptureState> = _captureState.asStateFlow()

    private val _completedCaptures = MutableSharedFlow<CompletedAudioCapture>(extraBufferCapacity = 8)
    val completedCaptures = _completedCaptures.asSharedFlow()

    @Volatile
    internal var pendingTrackChange: Triple<String, String, Long>? = null

    fun setCaptureModes(autoSplitEnabled: Boolean, silentMuteMode: Boolean) {
        _captureState.update {
            it.copy(autoSplitEnabled = autoSplitEnabled, silentMuteMode = silentMuteMode)
        }
    }

    fun updateActiveTrackMetadata(title: String, artist: String, durationMs: Long) {
        val state = _captureState.value
        if (!state.isCapturing) return
        val cleanTitle = title.trim().ifBlank { "Captured_Track" }
        val cleanArtist = artist.trim()
        val sameTrack = cleanTitle.equals(state.targetTitle, ignoreCase = true) &&
            cleanArtist.equals(state.targetArtist, ignoreCase = true)
        if (!sameTrack) {
            if (state.autoSplitEnabled) {
                pendingTrackChange = Triple(cleanTitle, cleanArtist, durationMs)
            }
        } else if (durationMs > 0L && state.expectedDurationMs <= 0L) {
            _captureState.update { it.copy(expectedDurationMs = durationMs) }
        }
    }

    internal fun shouldDiscardIncompleteRecording(recordedMs: Long, expectedMs: Long): Boolean {
        if (expectedMs < 15_000L) return false
        return recordedMs < (expectedMs * 0.70).toLong()
    }

    internal fun computeProgressPercent(recordedMs: Long, expectedMs: Long): Int {
        if (expectedMs <= 0L) return 0
        return ((recordedMs * 100L) / expectedMs).toInt().coerceIn(0, 100)
    }

    internal fun updateState(transform: (AudioCaptureState) -> AudioCaptureState) {
        _captureState.update(transform)
    }

    internal fun emitCompleted(item: CompletedAudioCapture) {
        _completedCaptures.tryEmit(item)
    }
}

class StreamAudioCaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "keydeck_stream_capture"
        private const val NOTIFICATION_ID = 4791
        const val ACTION_START_CAPTURE = "com.example.action.START_AUDIO_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.action.STOP_AUDIO_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA_INTENT = "data_intent"
        const val EXTRA_TITLE = "track_title"
        const val EXTRA_ARTIST = "track_artist"
        const val EXTRA_PACKAGE = "track_pkg"
        const val EXTRA_ALBUM_ID = "target_album_id"
        const val EXTRA_DURATION_MS = "expected_duration_ms"
        const val EXTRA_AUTO_SPLIT = "auto_split_enabled"
        const val EXTRA_SILENT_MUTE = "silent_mute_mode"

        fun startCapture(
            context: Context,
            resultCode: Int,
            data: Intent,
            title: String,
            artist: String,
            packageName: String,
            targetAlbumId: String? = null,
            expectedDurationMs: Long = 0L,
            autoSplitEnabled: Boolean = false,
            silentMuteMode: Boolean = false
        ) {
            val intent = Intent(context, StreamAudioCaptureService::class.java).apply {
                action = ACTION_START_CAPTURE
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA_INTENT, data)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_DURATION_MS, expectedDurationMs.coerceAtLeast(0L))
                putExtra(EXTRA_AUTO_SPLIT, autoSplitEnabled)
                putExtra(EXTRA_SILENT_MUTE, silentMuteMode)
                targetAlbumId?.let { putExtra(EXTRA_ALBUM_ID, it) }
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stopCapture(context: Context) {
            val intent = Intent(context, StreamAudioCaptureService::class.java).apply {
                action = ACTION_STOP_CAPTURE
            }
            runCatching { context.startService(intent) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var savedStreamVolumeBeforeMute: Int? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CAPTURE) {
            stopRecordingAndShutdown()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            StreamAudioCaptureRuntime.updateState {
                it.copy(isCapturing = false, statusText = "Захват потока требует Android 10+")
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_DATA_INTENT)
        }

        if (resultCode != Activity.RESULT_OK || projectionData == null) {
            StreamAudioCaptureRuntime.updateState {
                it.copy(isCapturing = false, statusText = "Разрешение на захват звука не получено")
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Captured_Track"
        val artist = intent?.getStringExtra(EXTRA_ARTIST).orEmpty()
        val pkg = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val albumId = intent?.getStringExtra(EXTRA_ALBUM_ID)
        val expectedDurationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 0L) ?: 0L
        val autoSplit = intent?.getBooleanExtra(EXTRA_AUTO_SPLIT, false) ?: false
        val silentMute = intent?.getBooleanExtra(EXTRA_SILENT_MUTE, false) ?: false

        val notif = buildNotification("Захват потока: $title")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }

        startPlaybackCapture(resultCode, projectionData, title, artist, pkg, albumId, expectedDurationMs, autoSplit, silentMute)
        return START_NOT_STICKY
    }

    private fun restoreMutedVolumeIfNeeded() {
        val prev = savedStreamVolumeBeforeMute ?: return
        savedStreamVolumeBeforeMute = null
        runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@runCatching
            am.setStreamVolume(AudioManager.STREAM_MUSIC, prev, 0)
        }
    }

    private fun applySilentMuteIfEnabled(silentMute: Boolean) {
        if (!silentMute) return
        runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@runCatching
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (cur > 0) {
                savedStreamVolumeBeforeMute = cur
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPlaybackCapture(
        resultCode: Int,
        data: Intent,
        initialTitle: String,
        initialArtist: String,
        pkg: String,
        albumId: String?,
        initialExpectedDurationMs: Long,
        autoSplit: Boolean,
        silentMute: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        captureJob?.cancel()
        StreamAudioCaptureRuntime.pendingTrackChange = null
        runCatching {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, data)
            mediaProjection = projection
            if (projection == null) {
                StreamAudioCaptureRuntime.updateState {
                    it.copy(isCapturing = false, statusText = "MediaProjection недоступен")
                }
                stopSelf()
                return
            }

            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            val sampleRate = 44100
            val channelMask = AudioFormat.CHANNEL_IN_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val bytesPerSecond = sampleRate * 2 * 2 // 44100 * stereo * 16-bit
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding).coerceAtLeast(8192)
            val audioFormat = AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build()

            val record = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBuf * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { record.release() }
                runCatching { projection.stop() }
                StreamAudioCaptureRuntime.updateState {
                    it.copy(isCapturing = false, statusText = "Нужно разрешение Микрофон/Audio для захвата")
                }
                stopSelf()
                return
            }

            audioRecord = record
            applySilentMuteIfEnabled(silentMute)

            StreamAudioCaptureRuntime.updateState {
                AudioCaptureState(
                    isCapturing = true,
                    targetTitle = initialTitle,
                    targetArtist = initialArtist,
                    targetPackage = pkg,
                    targetAlbumId = albumId,
                    expectedDurationMs = initialExpectedDurationMs,
                    progressPercent = 0,
                    autoSplitEnabled = autoSplit,
                    silentMuteMode = silentMute,
                    capturedFilesCount = 0,
                    discardReason = null,
                    elapsedSec = 0,
                    bytesWritten = 0L,
                    statusText = if (silentMute) "Тихий захват потока…" else "Идёт захват потока…"
                )
            }

            captureJob = serviceScope.launch {
                val buffer = ByteArray(minBuf)
                var curTitle = initialTitle
                var curArtist = initialArtist
                var curExpectedDurMs = initialExpectedDurationMs
                var filesSavedCount = 0

                try {
                    record.startRecording()
                    outer@ while (isActive && StreamAudioCaptureRuntime.captureState.value.isCapturing) {
                        val outputFile = createOutputWavFile(curTitle, curArtist)
                        var totalAudioBytes = 0L
                        val segmentStartMs = System.currentTimeMillis()
                        var switchedTrack: Triple<String, String, Long>? = null

                        RandomAccessFile(outputFile, "rw").use { raf ->
                            writeWavHeader(raf, sampleRate, 2, 16, 0L)
                            while (isActive && StreamAudioCaptureRuntime.captureState.value.isCapturing) {
                                val nextMeta = StreamAudioCaptureRuntime.pendingTrackChange
                                if (nextMeta != null && StreamAudioCaptureRuntime.captureState.value.autoSplitEnabled) {
                                    StreamAudioCaptureRuntime.pendingTrackChange = null
                                    switchedTrack = nextMeta
                                    break
                                }
                                val read = record.read(buffer, 0, buffer.size)
                                if (read > 0) {
                                    raf.write(buffer, 0, read)
                                    totalAudioBytes += read
                                    val elapsedMs = System.currentTimeMillis() - segmentStartMs
                                    val recordedAudioMs = (totalAudioBytes * 1000L) / bytesPerSecond
                                    val refMs = maxOf(elapsedMs, recordedAudioMs)
                                    val pct = StreamAudioCaptureRuntime.computeProgressPercent(refMs, curExpectedDurMs)
                                    val sec = (elapsedMs / 1000L).toInt()
                                    StreamAudioCaptureRuntime.updateState { cur ->
                                        cur.copy(
                                            elapsedSec = sec,
                                            bytesWritten = totalAudioBytes,
                                            progressPercent = pct
                                        )
                                    }
                                    if (curExpectedDurMs >= 15_000L &&
                                        !StreamAudioCaptureRuntime.captureState.value.autoSplitEnabled &&
                                        recordedAudioMs >= curExpectedDurMs + 4_000L
                                    ) {
                                        break
                                    }
                                }
                            }
                            raf.seek(0L)
                            writeWavHeader(raf, sampleRate, 2, 16, totalAudioBytes)
                        }

                        val recordedAudioMs = (totalAudioBytes * 1000L) / bytesPerSecond
                        val tooShort = StreamAudioCaptureRuntime.shouldDiscardIncompleteRecording(recordedAudioMs, curExpectedDurMs)
                        if (totalAudioBytes <= 4096L || tooShort) {
                            runCatching { outputFile.delete() }
                            val reason = if (tooShort) {
                                "Удалена неполная запись «$curTitle» (${recordedAudioMs / 1000}с из ${curExpectedDurMs / 1000}с)"
                            } else {
                                "Захват пустой записи отменён"
                            }
                            StreamAudioCaptureRuntime.updateState {
                                it.copy(discardReason = reason, statusText = reason)
                            }
                        } else {
                            filesSavedCount++
                            runCatching {
                                MediaScannerConnection.scanFile(
                                    applicationContext,
                                    arrayOf(outputFile.absolutePath),
                                    arrayOf("audio/wav"),
                                    null
                                )
                            }
                            StreamAudioCaptureRuntime.updateState {
                                it.copy(
                                    capturedFilesCount = filesSavedCount,
                                    lastSavedPath = outputFile.absolutePath,
                                    discardReason = null,
                                    statusText = "Сохранено: ${outputFile.name}"
                                )
                            }
                            StreamAudioCaptureRuntime.emitCompleted(
                                CompletedAudioCapture(
                                    filePath = outputFile.absolutePath,
                                    title = curTitle,
                                    artist = curArtist,
                                    targetAlbumId = albumId
                                )
                            )
                        }

                        if (switchedTrack != null && StreamAudioCaptureRuntime.captureState.value.isCapturing) {
                            curTitle = switchedTrack!!.first
                            curArtist = switchedTrack!!.second
                            curExpectedDurMs = switchedTrack!!.third
                            StreamAudioCaptureRuntime.updateState { cur ->
                                cur.copy(
                                    targetTitle = curTitle,
                                    targetArtist = curArtist,
                                    expectedDurationMs = curExpectedDurMs,
                                    progressPercent = 0,
                                    elapsedSec = 0,
                                    bytesWritten = 0L,
                                    statusText = "Авто-нарезка: $curTitle"
                                )
                            }
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.notify(NOTIFICATION_ID, buildNotification("Авто-нарезка: $curTitle"))
                        } else {
                            break@outer
                        }
                    }
                } finally {
                    restoreMutedVolumeIfNeeded()
                    runCatching { record.stop() }
                    runCatching { record.release() }
                    audioRecord = null
                    runCatching { projection.stop() }
                    mediaProjection = null
                    StreamAudioCaptureRuntime.updateState { it.copy(isCapturing = false) }
                    stopSelf()
                }
            }
        }.onFailure { err ->
            restoreMutedVolumeIfNeeded()
            StreamAudioCaptureRuntime.updateState {
                it.copy(isCapturing = false, statusText = err.message ?: "Ошибка захвата потока")
            }
            stopSelf()
        }
    }

    private fun stopRecordingAndShutdown() {
        StreamAudioCaptureRuntime.updateState { it.copy(isCapturing = false) }
        if (captureJob == null || captureJob?.isActive != true) {
            stopSelf()
        }
    }

    private fun createOutputWavFile(title: String, artist: String): File {
        val basePublic = runCatching {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            File(musicDir, "KeyDeck").apply { mkdirs() }
        }.getOrNull()?.takeIf { it.exists() && it.canWrite() }
            ?: File(filesDir, "music").apply { mkdirs() }

        val rawName = if (artist.isNotBlank()) "$artist - $title" else title
        val safeName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(64).ifBlank { "Captured" }
        var candidate = File(basePublic, "$safeName.wav")
        if (candidate.exists()) {
            candidate = File(basePublic, "${safeName}_${System.currentTimeMillis() % 10000}.wav")
        }
        return candidate
    }

    private fun writeWavHeader(
        raf: RandomAccessFile,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        totalAudioLen: Long
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = totalAudioLen + 36L
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        raf.write(header, 0, 44)
    }

    override fun onDestroy() {
        super.onDestroy()
        restoreMutedVolumeIfNeeded()
        captureJob?.cancel()
        runCatching { audioRecord?.stop(); audioRecord?.release() }
        runCatching { mediaProjection?.stop() }
        serviceScope.cancel()
        StreamAudioCaptureRuntime.updateState { it.copy(isCapturing = false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KeyDeck • Захват аудиопотока",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, StreamAudioCaptureService::class.java).apply { action = ACTION_STOP_CAPTURE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_symbian_music)
            .setContentTitle("KeyDeck Poweramp • Аудиозахват")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Стоп", stopIntent)
            .build()
    }
}
