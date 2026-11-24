package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.MusicCatalogAndLyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun RichCoverMedia(
    mediaPathOrUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {}
) {
    val cleanPath = mediaPathOrUrl?.trim().orEmpty()
    if (cleanPath.isEmpty()) {
        Box(modifier = modifier) { placeholder() }
        return
    }
    val lower = cleanPath.substringBefore('?').lowercase(Locale.ROOT)
    val localFile = remember(cleanPath) {
        if (!cleanPath.startsWith("http://") && !cleanPath.startsWith("https://")) {
            File(cleanPath).takeIf { it.exists() && it.canRead() }
        } else null
    }
    val isGif = localFile != null && lower.endsWith(".gif")
    val isVideo = localFile != null && (
        lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.endsWith(".mov")
    )

    when {
        isVideo && localFile != null -> {
            LoopingMutedVideoSurface(
                videoFile = localFile,
                modifier = modifier
            )
        }

        isGif && localFile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
            var drawable by remember(cleanPath) { mutableStateOf<Drawable?>(null) }
            LaunchedEffect(cleanPath) {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching {
                        val src = android.graphics.ImageDecoder.createSource(localFile)
                        android.graphics.ImageDecoder.decodeDrawable(src)
                    }.getOrNull()
                }
                drawable = decoded
            }
            val currentDrawable = drawable
            if (currentDrawable != null) {
                DisposableEffect(currentDrawable) {
                    if (currentDrawable is AnimatedImageDrawable) {
                        currentDrawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        currentDrawable.start()
                    }
                    onDispose {
                        if (currentDrawable is AnimatedImageDrawable) {
                            runCatching { currentDrawable.stop() }
                        }
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = if (contentScale == ContentScale.Crop) {
                                ImageView.ScaleType.CENTER_CROP
                            } else {
                                ImageView.ScaleType.FIT_CENTER
                            }
                        }
                    },
                    update = { iv ->
                        if (iv.drawable !== currentDrawable) {
                            iv.setImageDrawable(currentDrawable)
                        }
                        if (currentDrawable is AnimatedImageDrawable && !currentDrawable.isRunning) {
                            currentDrawable.start()
                        }
                    },
                    modifier = modifier
                )
            } else {
                Box(modifier = modifier) { placeholder() }
            }
        }

        else -> {
            var bmp by remember(cleanPath) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(cleanPath) {
                bmp = MusicCatalogAndLyricsRepository.loadArtworkBitmap(cleanPath)
            }
            val loaded = bmp
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = modifier
                )
            } else {
                Box(modifier = modifier) { placeholder() }
            }
        }
    }
}

@Composable
private fun LoopingMutedVideoSurface(
    videoFile: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerHolder = remember(videoFile.absolutePath) { arrayOfNulls<MediaPlayer>(1) }

    DisposableEffect(videoFile.absolutePath) {
        onDispose {
            runCatching {
                playerHolder[0]?.stop()
            }
            runCatching {
                playerHolder[0]?.release()
            }
            playerHolder[0] = null
        }
    }

    AndroidView(
        factory = {
            TextureView(context).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surfaceRef: Surface? = null

                    private fun applyCenterCrop(mp: MediaPlayer, viewW: Int, viewH: Int) {
                        val vw = mp.videoWidth
                        val vh = mp.videoHeight
                        if (vw <= 0 || vh <= 0 || viewW <= 0 || viewH <= 0) return
                        val scaleX = vw.toFloat() / viewW.toFloat()
                        val scaleY = vh.toFloat() / viewH.toFloat()
                        val maxScale = maxOf(viewW.toFloat() / vw.toFloat(), viewH.toFloat() / vh.toFloat())
                        val sx = (vw * maxScale) / viewW.toFloat()
                        val sy = (vh * maxScale) / viewH.toFloat()
                        val matrix = Matrix()
                        matrix.setScale(sx, sy, viewW / 2f, viewH / 2f)
                        setTransform(matrix)
                    }

                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val s = Surface(st)
                        surfaceRef = s
                        runCatching {
                            val mp = MediaPlayer().apply {
                                setDataSource(videoFile.absolutePath)
                                setSurface(s)
                                isLooping = true
                                setVolume(0f, 0f)
                                setOnPreparedListener { ready ->
                                    applyCenterCrop(ready, width, height)
                                    ready.setVolume(0f, 0f)
                                    ready.isLooping = true
                                    ready.start()
                                }
                                setOnVideoSizeChangedListener { ready, _, _ ->
                                    applyCenterCrop(ready, width, height)
                                }
                                prepareAsync()
                            }
                            playerHolder[0]?.release()
                            playerHolder[0] = mp
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        playerHolder[0]?.let { mp ->
                            runCatching { applyCenterCrop(mp, w, h) }
                        }
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching { playerHolder[0]?.stop() }
                        runCatching { playerHolder[0]?.release() }
                        playerHolder[0] = null
                        runCatching { surfaceRef?.release() }
                        surfaceRef = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
