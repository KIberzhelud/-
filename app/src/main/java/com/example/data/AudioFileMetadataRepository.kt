package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import com.example.model.LocalAudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class AudioFileMetadataReadResult(
    val title: String,
    val artist: String,
    val album: String,
    val hasEmbeddedCover: Boolean,
    val embeddedCoverBytes: ByteArray? = null
)

data class AudioFileMetadataUpdate(
    val title: String,
    val artist: String,
    val album: String,
    val newCoverBytes: ByteArray? = null,
    val clearEmbeddedCover: Boolean = false
)

object AudioFileMetadataRepository {

    private val OGG_CRC_TABLE: IntArray by lazy {
        IntArray(256).apply {
            for (i in 0 until 256) {
                var r = i shl 24
                for (j in 0 until 8) {
                    r = if ((r and Int.MIN_VALUE) != 0) {
                        (r shl 1) xor 0x04C11DB7
                    } else {
                        r shl 1
                    }
                }
                this[i] = r
            }
        }
    }

    suspend fun readTrackMetadata(
        context: Context,
        track: LocalAudioTrack
    ): AudioFileMetadataReadResult = withContext(Dispatchers.IO) {
        var title = track.title
        var artist = track.artist
        var album = track.album
        var coverBytes: ByteArray? = null

        val mmr = MediaMetadataRetriever()
        try {
            val filePath = track.filePath?.takeIf { it.isNotBlank() && File(it).exists() }
            if (filePath != null) {
                mmr.setDataSource(filePath)
            } else if (track.uriString.isNotBlank()) {
                mmr.setDataSource(context, Uri.parse(track.uriString))
            }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { title = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                ?.takeIf { it.isNotEmpty() && it != "<unknown>" }?.let { artist = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
                ?.takeIf { it.isNotEmpty() && it != "<unknown>" }?.let { album = it }
            coverBytes = mmr.embeddedPicture
        } catch (_: Throwable) {
        } finally {
            runCatching { mmr.release() }
        }

        val rawBytes = readTrackFileBytes(context, track)
        if (rawBytes != null) {
            val binaryMeta = parseBinaryMetadata(rawBytes)
            if (binaryMeta != null) {
                if (binaryMeta.title.isNotBlank()) title = binaryMeta.title
                if (binaryMeta.artist.isNotBlank()) artist = binaryMeta.artist
                if (binaryMeta.album.isNotBlank()) album = binaryMeta.album
                if (coverBytes == null && binaryMeta.embeddedCoverBytes != null) {
                    coverBytes = binaryMeta.embeddedCoverBytes
                }
            }
        }

        AudioFileMetadataReadResult(
            title = title,
            artist = artist,
            album = album,
            hasEmbeddedCover = coverBytes != null && coverBytes.isNotEmpty(),
            embeddedCoverBytes = coverBytes
        )
    }

    suspend fun decodeAndScaleCoverBytes(
        context: Context,
        uri: Uri,
        maxPx: Int = 720
    ): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            if (raw.isEmpty()) return@runCatching null
            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            if (bmp == null) {
                // Fallback: return raw image if small enough (e.g., already PNG/JPEG on Robolectric)
                return@runCatching if (raw.size <= 3 * 1024 * 1024) raw else null
            }
            val w = bmp.width.coerceAtLeast(1)
            val h = bmp.height.coerceAtLeast(1)
            val maxDim = maxOf(w, h)
            val scaled = if (maxDim > maxPx) {
                val scale = maxPx.toFloat() / maxDim.toFloat()
                Bitmap.createScaledBitmap(
                    bmp,
                    (w * scale).toInt().coerceAtLeast(1),
                    (h * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bmp
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            val bytes = out.toByteArray()
            if (scaled !== bmp) scaled.recycle()
            bmp.recycle()
            bytes
        }.getOrNull()
    }

    suspend fun cacheCoverBytesToFile(
        context: Context,
        keyHint: String,
        coverBytes: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        if (coverBytes.isEmpty()) return@withContext null
        runCatching {
            val dir = File(context.filesDir, "track_covers").apply { mkdirs() }
            val safe = keyHint.replace(Regex("[^a-zA-Z0-9_]"), "_").take(32).ifBlank { "cover" }
            val ext = if (isPngBytes(coverBytes)) "png" else "jpg"
            val out = File(dir, "emb_${safe}_${System.currentTimeMillis() % 100000}.$ext")
            FileOutputStream(out).use { it.write(coverBytes) }
            out.absolutePath
        }.getOrNull()
    }

    suspend fun writeTrackMetadata(
        context: Context,
        track: LocalAudioTrack,
        update: AudioFileMetadataUpdate
    ): Result<LocalAudioTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = update.title.trim().ifBlank { track.title }
            val cleanArtist = update.artist.trim()
            val cleanAlbum = update.album.trim()

            val originalBytes = readTrackFileBytes(context, track)
                ?: error("Не удалось прочитать аудиофайл на устройстве")

            val extHint = (track.filePath?.substringAfterLast('.', "")
                ?: track.displayPath.substringAfterLast('.', "")
                ?: track.formatLabel).lowercase(Locale.ROOT)

            val existingCover = if (update.newCoverBytes == null && !update.clearEmbeddedCover) {
                parseBinaryMetadata(originalBytes)?.embeddedCoverBytes
            } else {
                null
            }
            val targetCoverBytes: ByteArray? = when {
                update.clearEmbeddedCover -> null
                update.newCoverBytes != null && update.newCoverBytes.isNotEmpty() -> update.newCoverBytes
                else -> existingCover
            }

            val rewrittenBytes = rewriteAudioBytes(
                bytes = originalBytes,
                extHint = extHint,
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                coverBytes = targetCoverBytes
            ) ?: error("Формат файла ($extHint) не поддерживает прямую перезапись тегов")

            var updatedPath = track.filePath
            var updatedUriString = track.uriString

            val rawFile = track.filePath?.takeIf { it.isNotBlank() }?.let { File(it) }
            if (rawFile != null && rawFile.exists()) {
                val parent = rawFile.parentFile ?: error("Папка файла недоступна")
                val tmp = File(parent, ".keydeck_tag_${System.currentTimeMillis()}_${rawFile.name}")
                FileOutputStream(tmp).use { fos ->
                    fos.write(rewrittenBytes)
                    fos.fd.sync()
                }
                if (!tmp.renameTo(rawFile)) {
                    FileOutputStream(rawFile, false).use { fos ->
                        fos.write(rewrittenBytes)
                        fos.fd.sync()
                    }
                    tmp.delete()
                }
                updatedPath = rawFile.absolutePath
                runCatching {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(rawFile.absolutePath),
                        null,
                        null
                    )
                }
                syncMediaStoreMetadataByPath(context, rawFile.absolutePath, cleanTitle, cleanArtist, cleanAlbum)
            } else if (track.uriString.startsWith("content://")) {
                val uri = Uri.parse(track.uriString)
                context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                    os.write(rewrittenBytes)
                    os.flush()
                } ?: error("Нет прав на запись в выбранный аудиофайл")

                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, cleanTitle)
                    put(MediaStore.Audio.Media.ARTIST, cleanArtist.ifBlank { "<unknown>" })
                    put(MediaStore.Audio.Media.ALBUM, cleanAlbum.ifBlank { "<unknown>" })
                }
                runCatching { context.contentResolver.update(uri, values, null, null) }
            } else {
                error("Файл недоступен для редактирования")
            }

            track.copy(
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                filePath = updatedPath,
                uriString = updatedUriString
            )
        }
    }

    private fun readTrackFileBytes(context: Context, track: LocalAudioTrack): ByteArray? {
        val file = track.filePath?.takeIf { it.isNotBlank() }?.let { File(it) }
        if (file != null && file.exists() && file.canRead()) {
            return runCatching { file.readBytes() }.getOrNull()
        }
        if (track.uriString.startsWith("content://")) {
            return runCatching {
                context.contentResolver.openInputStream(Uri.parse(track.uriString))?.use { it.readBytes() }
            }.getOrNull()
        }
        if (track.uriString.startsWith("file://")) {
            val f = Uri.parse(track.uriString).path?.let { File(it) }
            if (f != null && f.exists() && f.canRead()) {
                return runCatching { f.readBytes() }.getOrNull()
            }
        }
        return null
    }

    private fun syncMediaStoreMetadataByPath(
        context: Context,
        absPath: String,
        title: String,
        artist: String,
        album: String
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) return
        runCatching {
            @Suppress("DEPRECATION")
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
            }
            @Suppress("DEPRECATION")
            context.contentResolver.update(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values,
                "${MediaStore.Audio.Media.DATA}=?",
                arrayOf(absPath)
            )
        }
    }

    internal fun rewriteAudioBytes(
        bytes: ByteArray,
        extHint: String,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ): ByteArray? {
        return when {
            startsWithAscii(bytes, 0, "fLaC") || extHint == "flac" -> {
                if (startsWithAscii(bytes, 0, "fLaC")) {
                    rewriteFlacBytes(bytes, title, artist, album, coverBytes)
                } else null
            }
            startsWithAscii(bytes, 0, "RIFF") && startsWithAscii(bytes, 8, "WAVE") -> {
                rewriteWavBytes(bytes, title, artist, album, coverBytes)
            }
            startsWithAscii(bytes, 0, "OggS") -> {
                rewriteOggBytes(bytes, title, artist, album, coverBytes)
            }
            bytes.size >= 12 && startsWithAscii(bytes, 4, "ftyp") -> {
                rewriteMp4Bytes(bytes, title, artist, album, coverBytes)
            }
            startsWithAscii(bytes, 0, "ID3") || extHint in setOf("mp3", "aac") || isMp3Sync(bytes) -> {
                rewriteMp3Id3v23(bytes, title, artist, album, coverBytes)
            }
            else -> null
        }
    }

    internal fun parseBinaryMetadata(bytes: ByteArray): AudioFileMetadataReadResult? {
        return when {
            startsWithAscii(bytes, 0, "fLaC") -> parseFlacMetadata(bytes)
            startsWithAscii(bytes, 0, "ID3") -> parseId3v2Metadata(bytes, 0)
            startsWithAscii(bytes, 0, "RIFF") && startsWithAscii(bytes, 8, "WAVE") -> parseWavMetadata(bytes)
            startsWithAscii(bytes, 0, "OggS") -> parseOggMetadata(bytes)
            bytes.size >= 12 && startsWithAscii(bytes, 4, "ftyp") -> parseMp4Metadata(bytes)
            else -> null
        }
    }

    // =========================================================================
    // MP3 / ID3v2.3 Implementation
    // =========================================================================

    private fun rewriteMp3Id3v23(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ): ByteArray {
        val audioStart = if (startsWithAscii(bytes, 0, "ID3") && bytes.size >= 10) {
            val flags = bytes[5].toInt() and 0xFF
            val tagSize = readSynchsafeInt(bytes, 6)
            val footerSize = if ((flags and 0x10) != 0) 10 else 0
            (10 + tagSize + footerSize).coerceIn(0, bytes.size)
        } else {
            0
        }

        // Strip trailing ID3v1 TAG if present so players don't read stale ID3v1 fields
        var audioEnd = bytes.size
        if (audioEnd - audioStart >= 128 && startsWithAscii(bytes, audioEnd - 128, "TAG")) {
            audioEnd -= 128
        }

        val frames = ByteArrayOutputStream()
        if (title.isNotEmpty()) writeId3v23TextFrame(frames, "TIT2", title)
        if (artist.isNotEmpty()) writeId3v23TextFrame(frames, "TPE1", artist)
        if (album.isNotEmpty()) writeId3v23TextFrame(frames, "TALB", album)
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            writeId3v23ApicFrame(frames, coverBytes)
        }

        val frameBytes = frames.toByteArray()
        val out = ByteArrayOutputStream(10 + frameBytes.size + (audioEnd - audioStart) + 128)
        out.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        out.write(3) // v2.3
        out.write(0) // rev 0
        out.write(0) // flags
        writeSynchsafeInt(out, frameBytes.size)
        out.write(frameBytes)
        out.write(bytes, audioStart, audioEnd - audioStart)
        writeId3v1Footer(out, title, artist, album)
        return out.toByteArray()
    }

    private fun writeId3v23TextFrame(out: ByteArrayOutputStream, frameId: String, value: String) {
        val textBytes = ("\uFEFF" + value).toByteArray(StandardCharsets.UTF_16LE)
        val payloadSize = 1 + textBytes.size
        out.write(frameId.toByteArray(StandardCharsets.US_ASCII))
        writeInt32BE(out, payloadSize)
        out.write(0)
        out.write(0)
        out.write(1) // UTF-16 with BOM
        out.write(textBytes)
    }

    private fun writeId3v23ApicFrame(out: ByteArrayOutputStream, imageBytes: ByteArray) {
        val mime = if (isPngBytes(imageBytes)) "image/png" else "image/jpeg"
        val mimeBytes = mime.toByteArray(StandardCharsets.ISO_8859_1)
        val payload = ByteArrayOutputStream(1 + mimeBytes.size + 1 + 1 + 1 + imageBytes.size)
        payload.write(0) // ISO-8859-1 description
        payload.write(mimeBytes)
        payload.write(0)
        payload.write(3) // Cover (front)
        payload.write(0) // Empty description
        payload.write(imageBytes)

        val pBytes = payload.toByteArray()
        out.write("APIC".toByteArray(StandardCharsets.US_ASCII))
        writeInt32BE(out, pBytes.size)
        out.write(0)
        out.write(0)
        out.write(pBytes)
    }

    private fun writeId3v1Footer(out: ByteArrayOutputStream, title: String, artist: String, album: String) {
        out.write("TAG".toByteArray(StandardCharsets.US_ASCII))
        out.write(fixedPaddedField(title, 30))
        out.write(fixedPaddedField(artist, 30))
        out.write(fixedPaddedField(album, 30))
        out.write(ByteArray(4)) // year
        out.write(ByteArray(30)) // comment
        out.write(255) // genre unknown
    }

    private fun fixedPaddedField(s: String, len: Int): ByteArray {
        val raw = s.toByteArray(StandardCharsets.ISO_8859_1)
        val out = ByteArray(len)
        System.arraycopy(raw, 0, out, 0, minOf(raw.size, len))
        return out
    }

    private fun parseId3v2Metadata(bytes: ByteArray, offset: Int): AudioFileMetadataReadResult? {
        if (offset + 10 > bytes.size || !startsWithAscii(bytes, offset, "ID3")) return null
        val major = bytes[offset + 3].toInt() and 0xFF
        val flags = bytes[offset + 5].toInt() and 0xFF
        val tagSize = readSynchsafeInt(bytes, offset + 6)
        var pos = offset + 10
        val end = (pos + tagSize).coerceAtMost(bytes.size)
        if ((flags and 0x40) != 0 && pos + 4 <= end) {
            val extLen = if (major >= 4) readSynchsafeInt(bytes, pos) else readInt32BE(bytes, pos) + 4
            pos += extLen
        }
        var title = ""
        var artist = ""
        var album = ""
        var cover: ByteArray? = null

        if (major < 3) return null
        while (pos + 10 <= end) {
            if (bytes[pos].toInt() == 0) break
            val frameId = String(bytes, pos, 4, StandardCharsets.US_ASCII)
            val fSize = if (major >= 4) readSynchsafeInt(bytes, pos + 4) else readInt32BE(bytes, pos + 4)
            pos += 10
            if (fSize <= 0 || pos + fSize > end) break
            when (frameId) {
                "TIT2" -> decodeId3Text(bytes, pos, fSize)?.let { title = it }
                "TPE1" -> decodeId3Text(bytes, pos, fSize)?.let { artist = it }
                "TALB" -> decodeId3Text(bytes, pos, fSize)?.let { album = it }
                "APIC" -> if (cover == null) cover = decodeId3Apic(bytes, pos, fSize)
            }
            pos += fSize
        }
        return AudioFileMetadataReadResult(title, artist, album, cover != null, cover)
    }

    private fun decodeId3Text(bytes: ByteArray, start: Int, len: Int): String? {
        if (len <= 1) return null
        val enc = bytes[start].toInt() and 0xFF
        val raw = bytes.copyOfRange(start + 1, start + len)
        val decoded = when (enc) {
            0 -> String(raw, StandardCharsets.ISO_8859_1)
            1 -> decodeUtf16WithBom(raw)
            2 -> String(raw, StandardCharsets.UTF_16BE)
            else -> String(raw, StandardCharsets.UTF_8)
        }
        return decoded.trim('\u0000', ' ', '\uFEFF')
    }

    private fun decodeUtf16WithBom(raw: ByteArray): String {
        if (raw.size >= 2) {
            val b0 = raw[0].toInt() and 0xFF
            val b1 = raw[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) {
                return String(raw, 2, raw.size - 2, StandardCharsets.UTF_16LE)
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return String(raw, 2, raw.size - 2, StandardCharsets.UTF_16BE)
            }
        }
        return String(raw, StandardCharsets.UTF_16)
    }

    private fun decodeId3Apic(bytes: ByteArray, start: Int, len: Int): ByteArray? {
        if (len < 4) return null
        val enc = bytes[start].toInt() and 0xFF
        var p = start + 1
        val end = start + len
        while (p < end && bytes[p].toInt() != 0) p++
        p++ // null after MIME
        if (p >= end) return null
        p++ // picture type
        if (enc == 1 || enc == 2) {
            while (p + 1 < end && !(bytes[p].toInt() == 0 && bytes[p + 1].toInt() == 0)) p += 2
            p += 2
        } else {
            while (p < end && bytes[p].toInt() != 0) p++
            p++
        }
        if (p >= end) return null
        return bytes.copyOfRange(p, end)
    }

    // =========================================================================
    // FLAC Implementation (STREAMINFO preserved + VORBIS_COMMENT + PICTURE)
    // =========================================================================

    private fun rewriteFlacBytes(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ): ByteArray? {
        if (bytes.size < 8 || !startsWithAscii(bytes, 0, "fLaC")) return null
        var pos = 4
        var vendor = "KeyDeck"
        val preservedComments = mutableListOf<String>()
        val otherBlocks = mutableListOf<Pair<Int, ByteArray>>()
        var streamInfo: ByteArray? = null

        var isLast = false
        while (!isLast && pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val len = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + len > bytes.size) return null
            val payload = bytes.copyOfRange(pos, pos + len)
            pos += len
            when (blockType) {
                0 -> streamInfo = payload
                4 -> {
                    val parsed = parseVorbisCommentPayload(payload)
                    if (parsed.first.isNotBlank()) vendor = parsed.first
                    parsed.second.forEach { entry ->
                        val key = entry.substringBefore('=').uppercase(Locale.ROOT)
                        if (key != "TITLE" && key != "ARTIST" && key != "ALBUM") {
                            preservedComments.add(entry)
                        }
                    }
                }
                6 -> Unit // Replaced / cleared via coverBytes
                else -> otherBlocks.add(blockType to payload)
            }
        }
        val finalStreamInfo = streamInfo ?: return null

        val newComments = mutableListOf<String>()
        if (title.isNotEmpty()) newComments.add("TITLE=$title")
        if (artist.isNotEmpty()) newComments.add("ARTIST=$artist")
        if (album.isNotEmpty()) newComments.add("ALBUM=$album")
        newComments.addAll(preservedComments)

        val vcPayload = buildVorbisCommentPayload(vendor, newComments)
        val blocksToWrite = mutableListOf<Pair<Int, ByteArray>>()
        blocksToWrite.add(0 to finalStreamInfo)
        blocksToWrite.addAll(otherBlocks)
        blocksToWrite.add(4 to vcPayload)
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            blocksToWrite.add(6 to buildFlacPicturePayload(coverBytes))
        }

        val out = ByteArrayOutputStream(bytes.size + 4096)
        out.write("fLaC".toByteArray(StandardCharsets.US_ASCII))
        blocksToWrite.forEachIndexed { index, (type, data) ->
            val lastFlag = if (index == blocksToWrite.lastIndex) 0x80 else 0x00
            out.write(lastFlag or (type and 0x7F))
            out.write((data.size ushr 16) and 0xFF)
            out.write((data.size ushr 8) and 0xFF)
            out.write(data.size and 0xFF)
            out.write(data)
        }
        out.write(bytes, pos, bytes.size - pos)
        return out.toByteArray()
    }

    private fun parseFlacMetadata(bytes: ByteArray): AudioFileMetadataReadResult? {
        if (bytes.size < 8 || !startsWithAscii(bytes, 0, "fLaC")) return null
        var pos = 4
        var title = ""
        var artist = ""
        var album = ""
        var cover: ByteArray? = null
        var isLast = false
        while (!isLast && pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            isLast = (header and 0x80) != 0
            val type = header and 0x7F
            val len = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + len > bytes.size) break
            if (type == 4) {
                val parsed = parseVorbisCommentPayload(bytes.copyOfRange(pos, pos + len))
                parsed.second.forEach { entry ->
                    val idx = entry.indexOf('=')
                    if (idx > 0) {
                        val k = entry.substring(0, idx).uppercase(Locale.ROOT)
                        val v = entry.substring(idx + 1).trim()
                        when (k) {
                            "TITLE" -> title = v
                            "ARTIST" -> artist = v
                            "ALBUM" -> album = v
                        }
                    }
                }
            } else if (type == 6 && cover == null) {
                cover = parseFlacPictureBytes(bytes.copyOfRange(pos, pos + len))
            }
            pos += len
        }
        return AudioFileMetadataReadResult(title, artist, album, cover != null, cover)
    }

    private fun buildFlacPicturePayload(coverBytes: ByteArray): ByteArray {
        val mime = (if (isPngBytes(coverBytes)) "image/png" else "image/jpeg")
            .toByteArray(StandardCharsets.US_ASCII)
        val out = ByteArrayOutputStream(32 + mime.size + coverBytes.size)
        writeInt32BE(out, 3) // Cover (front)
        writeInt32BE(out, mime.size)
        out.write(mime)
        writeInt32BE(out, 0) // description len
        writeInt32BE(out, 0) // width
        writeInt32BE(out, 0) // height
        writeInt32BE(out, 0) // depth
        writeInt32BE(out, 0) // indexed colors
        writeInt32BE(out, coverBytes.size)
        out.write(coverBytes)
        return out.toByteArray()
    }

    private fun parseFlacPictureBytes(payload: ByteArray): ByteArray? {
        if (payload.size < 32) return null
        var p = 4
        val mimeLen = readInt32BE(payload, p); p += 4 + mimeLen
        if (p + 4 > payload.size) return null
        val descLen = readInt32BE(payload, p); p += 4 + descLen + 16
        if (p + 4 > payload.size) return null
        val picLen = readInt32BE(payload, p); p += 4
        if (picLen <= 0 || p + picLen > payload.size) return null
        return payload.copyOfRange(p, p + picLen)
    }

    private fun parseVorbisCommentPayload(payload: ByteArray): Pair<String, List<String>> {
        if (payload.size < 8) return "KeyDeck" to emptyList()
        var p = 0
        val vLen = readInt32LE(payload, p).coerceAtLeast(0); p += 4
        if (p + vLen + 4 > payload.size) return "KeyDeck" to emptyList()
        val vendor = String(payload, p, vLen, StandardCharsets.UTF_8); p += vLen
        val count = readInt32LE(payload, p).coerceAtLeast(0); p += 4
        val list = mutableListOf<String>()
        repeat(minOf(count, 512)) {
            if (p + 4 > payload.size) return vendor to list
            val cLen = readInt32LE(payload, p).coerceAtLeast(0); p += 4
            if (p + cLen > payload.size) return vendor to list
            list.add(String(payload, p, cLen, StandardCharsets.UTF_8))
            p += cLen
        }
        return vendor to list
    }

    private fun buildVorbisCommentPayload(vendor: String, comments: List<String>): ByteArray {
        val vBytes = vendor.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()
        writeInt32LE(out, vBytes.size)
        out.write(vBytes)
        writeInt32LE(out, comments.size)
        comments.forEach { c ->
            val cb = c.toByteArray(StandardCharsets.UTF_8)
            writeInt32LE(out, cb.size)
            out.write(cb)
        }
        return out.toByteArray()
    }

    // =========================================================================
    // M4A / MP4 ISO-BMFF Implementation (with stco/co64 offset adjustment)
    // =========================================================================

    private data class Mp4Box(val type: String, val start: Int, val headerSize: Int, val size: Int) {
        val end: Int get() = start + size
        val contentStart: Int get() = start + headerSize
    }

    private fun listMp4Boxes(bytes: ByteArray, start: Int, end: Int): List<Mp4Box> {
        val out = mutableListOf<Mp4Box>()
        var p = start
        while (p + 8 <= end) {
            val size32 = readInt32BE(bytes, p).toLong() and 0xFFFFFFFFL
            val type = String(bytes, p + 4, 4, StandardCharsets.ISO_8859_1)
            val (boxSize, hdr) = when (size32) {
                0L -> (end - p) to 8
                1L -> {
                    if (p + 16 > end) break
                    val ext = ByteBuffer.wrap(bytes, p + 8, 8).order(ByteOrder.BIG_ENDIAN).long
                    ext.toInt().coerceAtMost(end - p) to 16
                }
                else -> size32.toInt() to 8
            }
            if (boxSize < hdr || p + boxSize > end) break
            out.add(Mp4Box(type, p, hdr, boxSize))
            p += boxSize
        }
        return out
    }

    private fun buildMp4Atom(type: String, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(8 + content.size)
        writeInt32BE(out, 8 + content.size)
        out.write(type.toByteArray(StandardCharsets.ISO_8859_1))
        out.write(content)
        return out.toByteArray()
    }

    private fun buildMp4TextTagAtom(atomType: String, text: String): ByteArray {
        val txt = text.toByteArray(StandardCharsets.UTF_8)
        val dataContent = ByteArrayOutputStream(8 + txt.size).apply {
            writeInt32BE(this, 1) // UTF-8
            writeInt32BE(this, 0)
            write(txt)
        }.toByteArray()
        return buildMp4Atom(atomType, buildMp4Atom("data", dataContent))
    }

    private fun buildMp4CoverAtom(coverBytes: ByteArray): ByteArray {
        val typeCode = if (isPngBytes(coverBytes)) 14 else 13
        val dataContent = ByteArrayOutputStream(8 + coverBytes.size).apply {
            writeInt32BE(this, typeCode)
            writeInt32BE(this, 0)
            write(coverBytes)
        }.toByteArray()
        return buildMp4Atom("covr", buildMp4Atom("data", dataContent))
    }

    private fun rewriteMp4Bytes(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ): ByteArray? {
        val topBoxes = listMp4Boxes(bytes, 0, bytes.size)
        val moov = topBoxes.firstOrNull { it.type == "moov" } ?: return null
        val moovChildren = listMp4Boxes(bytes, moov.contentStart, moov.end)

        val ilstBody = ByteArrayOutputStream().apply {
            if (title.isNotEmpty()) write(buildMp4TextTagAtom("\u00A9nam", title))
            if (artist.isNotEmpty()) write(buildMp4TextTagAtom("\u00A9ART", artist))
            if (album.isNotEmpty()) write(buildMp4TextTagAtom("\u00A9alb", album))
            if (coverBytes != null && coverBytes.isNotEmpty()) write(buildMp4CoverAtom(coverBytes))
        }.toByteArray()

        val hdlrPayload = ByteArrayOutputStream(26).apply {
            writeInt32BE(this, 0) // version/flags
            writeInt32BE(this, 0)
            write("mdir".toByteArray(StandardCharsets.ISO_8859_1))
            write("appl".toByteArray(StandardCharsets.ISO_8859_1))
            writeInt32BE(this, 0)
            writeInt32BE(this, 0)
            writeInt32BE(this, 0)
            write(0)
        }.toByteArray()

        val metaBody = ByteArrayOutputStream().apply {
            writeInt32BE(this, 0) // version + flags for meta FullBox
            write(buildMp4Atom("hdlr", hdlrPayload))
            write(buildMp4Atom("ilst", ilstBody))
        }.toByteArray()
        val newUdta = buildMp4Atom("udta", buildMp4Atom("meta", metaBody))

        val otherChildrenBytes = ByteArrayOutputStream()
        moovChildren.filterNot { it.type == "udta" }.forEach { child ->
            otherChildrenBytes.write(bytes, child.start, child.size)
        }
        val otherRaw = otherChildrenBytes.toByteArray()
        val newMoovContentSize = otherRaw.size + newUdta.size
        val newMoovTotalSize = 8 + newMoovContentSize
        val delta = newMoovTotalSize - moov.size

        // Patch chunk offsets in trak -> mdia -> minf -> stbl -> stco / co64 when moov shifts mdat
        val patchedOther = otherRaw.copyOf()
        if (delta != 0) {
            patchMp4OffsetsInPlace(patchedOther, 0, patchedOther.size, moov.end, delta)
        }

        val out = ByteArrayOutputStream(bytes.size + delta.coerceAtLeast(0))
        topBoxes.forEach { box ->
            if (box.type == "moov") {
                writeInt32BE(out, newMoovTotalSize)
                out.write("moov".toByteArray(StandardCharsets.ISO_8859_1))
                out.write(patchedOther)
                out.write(newUdta)
            } else {
                out.write(bytes, box.start, box.size)
            }
        }
        return out.toByteArray()
    }

    private fun patchMp4OffsetsInPlace(buf: ByteArray, start: Int, end: Int, thresholdOffset: Int, delta: Int) {
        val boxes = listMp4Boxes(buf, start, end)
        for (b in boxes) {
            when (b.type) {
                "trak", "mdia", "minf", "stbl" -> patchMp4OffsetsInPlace(buf, b.contentStart, b.end, thresholdOffset, delta)
                "stco" -> {
                    if (b.contentStart + 8 <= b.end) {
                        val count = readInt32BE(buf, b.contentStart + 4)
                        var p = b.contentStart + 8
                        repeat(count) {
                            if (p + 4 <= b.end) {
                                val oldOff = readInt32BE(buf, p).toLong() and 0xFFFFFFFFL
                                if (oldOff >= thresholdOffset.toLong()) {
                                    val patched = (oldOff + delta).toInt()
                                    buf[p] = ((patched ushr 24) and 0xFF).toByte()
                                    buf[p + 1] = ((patched ushr 16) and 0xFF).toByte()
                                    buf[p + 2] = ((patched ushr 8) and 0xFF).toByte()
                                    buf[p + 3] = (patched and 0xFF).toByte()
                                }
                                p += 4
                            }
                        }
                    }
                }
                "co64" -> {
                    if (b.contentStart + 8 <= b.end) {
                        val count = readInt32BE(buf, b.contentStart + 4)
                        var p = b.contentStart + 8
                        repeat(count) {
                            if (p + 8 <= b.end) {
                                val oldOff = ByteBuffer.wrap(buf, p, 8).order(ByteOrder.BIG_ENDIAN).long
                                if (oldOff >= thresholdOffset.toLong()) {
                                    ByteBuffer.wrap(buf, p, 8).order(ByteOrder.BIG_ENDIAN).putLong(oldOff + delta)
                                }
                                p += 8
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseMp4Metadata(bytes: ByteArray): AudioFileMetadataReadResult? {
        val top = listMp4Boxes(bytes, 0, bytes.size)
        val moov = top.firstOrNull { it.type == "moov" } ?: return null
        val udta = listMp4Boxes(bytes, moov.contentStart, moov.end).firstOrNull { it.type == "udta" } ?: return null
        val meta = listMp4Boxes(bytes, udta.contentStart, udta.end).firstOrNull { it.type == "meta" } ?: return null
        if (meta.contentStart + 4 > meta.end) return null
        val ilst = listMp4Boxes(bytes, meta.contentStart + 4, meta.end).firstOrNull { it.type == "ilst" } ?: return null
        var title = ""
        var artist = ""
        var album = ""
        var cover: ByteArray? = null
        listMp4Boxes(bytes, ilst.contentStart, ilst.end).forEach { atom ->
            val data = listMp4Boxes(bytes, atom.contentStart, atom.end).firstOrNull { it.type == "data" } ?: return@forEach
            val valStart = data.contentStart + 8
            if (valStart >= data.end) return@forEach
            val raw = bytes.copyOfRange(valStart, data.end)
            when (atom.type) {
                "\u00A9nam" -> title = String(raw, StandardCharsets.UTF_8).trim()
                "\u00A9ART" -> artist = String(raw, StandardCharsets.UTF_8).trim()
                "\u00A9alb" -> album = String(raw, StandardCharsets.UTF_8).trim()
                "covr" -> if (cover == null) cover = raw
            }
        }
        return AudioFileMetadataReadResult(title, artist, album, cover != null, cover)
    }

    // =========================================================================
    // OGG / OPUS VorbisComment Page Rewriter (with CRC-32)
    // =========================================================================

    private data class OggPage(
        val start: Int,
        val end: Int,
        val headerType: Int,
        val granule: ByteArray,
        val serial: Int,
        val seqNo: Int,
        val packetBytes: ByteArray
    )

    private fun readOggPage(bytes: ByteArray, offset: Int): OggPage? {
        if (offset + 27 > bytes.size || !startsWithAscii(bytes, offset, "OggS")) return null
        val headerType = bytes[offset + 5].toInt() and 0xFF
        val granule = bytes.copyOfRange(offset + 6, offset + 14)
        val serial = readInt32LE(bytes, offset + 14)
        val seqNo = readInt32LE(bytes, offset + 18)
        val segCount = bytes[offset + 26].toInt() and 0xFF
        if (offset + 27 + segCount > bytes.size) return null
        var dataLen = 0
        for (i in 0 until segCount) {
            dataLen += bytes[offset + 27 + i].toInt() and 0xFF
        }
        val payloadStart = offset + 27 + segCount
        val end = payloadStart + dataLen
        if (end > bytes.size) return null
        return OggPage(
            start = offset,
            end = end,
            headerType = headerType,
            granule = granule,
            serial = serial,
            seqNo = seqNo,
            packetBytes = bytes.copyOfRange(payloadStart, end)
        )
    }

    private fun rewriteOggBytes(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ): ByteArray? {
        val page0 = readOggPage(bytes, 0) ?: return null
        val page1 = readOggPage(bytes, page0.end) ?: return null
        val pkt = page1.packetBytes

        val (prefix, oldVendor, oldComments, suffix) = when {
            pkt.size > 7 && pkt[0] == 3.toByte() && startsWithAscii(pkt, 1, "vorbis") -> {
                val p = pkt.copyOfRange(0, 7)
                val parsed = parseVorbisCommentPayload(pkt.copyOfRange(7, pkt.size))
                Tuple4(p, parsed.first, parsed.second, byteArrayOf(1))
            }
            pkt.size >= 8 && startsWithAscii(pkt, 0, "OpusTags") -> {
                val p = "OpusTags".toByteArray(StandardCharsets.US_ASCII)
                val parsed = parseVorbisCommentPayload(pkt.copyOfRange(8, pkt.size))
                Tuple4(p, parsed.first, parsed.second, ByteArray(0))
            }
            else -> return null
        }

        val comments = mutableListOf<String>()
        if (title.isNotEmpty()) comments.add("TITLE=$title")
        if (artist.isNotEmpty()) comments.add("ARTIST=$artist")
        if (album.isNotEmpty()) comments.add("ALBUM=$album")
        oldComments.forEach { entry ->
            val k = entry.substringBefore('=').uppercase(Locale.ROOT)
            if (k != "TITLE" && k != "ARTIST" && k != "ALBUM" && k != "METADATA_BLOCK_PICTURE") {
                comments.add(entry)
            }
        }
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            val picBlock = buildFlacPicturePayload(coverBytes)
            comments.add("METADATA_BLOCK_PICTURE=" + Base64.encodeToString(picBlock, Base64.NO_WRAP))
        }

        val newPacket = ByteArrayOutputStream().apply {
            write(prefix)
            write(buildVorbisCommentPayload(oldVendor, comments))
            write(suffix)
        }.toByteArray()

        // Split into valid Ogg pages (<= 65025 bytes per page)
        val out = ByteArrayOutputStream(bytes.size + 4096)
        out.write(bytes, 0, page0.end)

        var seqCounter = page0.seqNo + 1
        var pktOff = 0
        var firstCommentPage = true
        while (pktOff < newPacket.size) {
            val chunkLen = minOf(newPacket.size - pktOff, 255 * 255)
            val chunk = newPacket.copyOfRange(pktOff, pktOff + chunkLen)
            pktOff += chunkLen
            val headerType = if (firstCommentPage) 0x00 else 0x01
            firstCommentPage = false
            out.write(buildOggPage(headerType, page1.granule, page1.serial, seqCounter++, chunk))
        }

        // Copy remaining Ogg audio pages, renumbering sequence numbers if needed
        var scanPos = page1.end
        while (scanPos < bytes.size) {
            val page = readOggPage(bytes, scanPos)
            if (page == null) {
                out.write(bytes, scanPos, bytes.size - scanPos)
                break
            }
            val pageCopy = bytes.copyOfRange(page.start, page.end)
            writeInt32LEToArray(pageCopy, 18, seqCounter++)
            pageCopy[22] = 0; pageCopy[23] = 0; pageCopy[24] = 0; pageCopy[25] = 0
            val crc = computeOggCrc(pageCopy)
            writeInt32LEToArray(pageCopy, 22, crc)
            out.write(pageCopy)
            scanPos = page.end
        }
        return out.toByteArray()
    }

    private fun parseOggMetadata(bytes: ByteArray): AudioFileMetadataReadResult? {
        val p0 = readOggPage(bytes, 0) ?: return null
        val p1 = readOggPage(bytes, p0.end) ?: return null
        val pkt = p1.packetBytes
        val payload = when {
            pkt.size > 7 && pkt[0] == 3.toByte() && startsWithAscii(pkt, 1, "vorbis") ->
                pkt.copyOfRange(7, pkt.size)
            pkt.size >= 8 && startsWithAscii(pkt, 0, "OpusTags") ->
                pkt.copyOfRange(8, pkt.size)
            else -> return null
        }
        val (_, list) = parseVorbisCommentPayload(payload)
        var title = ""
        var artist = ""
        var album = ""
        var cover: ByteArray? = null
        list.forEach { entry ->
            val idx = entry.indexOf('=')
            if (idx > 0) {
                val k = entry.substring(0, idx).uppercase(Locale.ROOT)
                val v = entry.substring(idx + 1).trim()
                when (k) {
                    "TITLE" -> title = v
                    "ARTIST" -> artist = v
                    "ALBUM" -> album = v
                    "METADATA_BLOCK_PICTURE" -> if (cover == null) {
                        val decoded = runCatching { Base64.decode(v, Base64.DEFAULT) }.getOrNull()
                        if (decoded != null) cover = parseFlacPictureBytes(decoded)
                    }
                }
            }
        }
        return AudioFileMetadataReadResult(title, artist, album, cover != null, cover)
    }

    private fun buildOggPage(
        headerType: Int,
        granule: ByteArray,
        serial: Int,
        seqNo: Int,
        payload: ByteArray
    ): ByteArray {
        val fullSegs = payload.size / 255
        val rem = payload.size % 255
        val segTable = ByteArray(fullSegs + 1)
        for (i in 0 until fullSegs) segTable[i] = 255.toByte()
        segTable[fullSegs] = rem.toByte()

        val page = ByteArray(27 + segTable.size + payload.size)
        "OggS".toByteArray(StandardCharsets.US_ASCII).copyInto(page, 0)
        page[4] = 0 // version
        page[5] = (headerType and 0xFF).toByte()
        granule.copyInto(page, 6, 0, minOf(8, granule.size))
        writeInt32LEToArray(page, 14, serial)
        writeInt32LEToArray(page, 18, seqNo)
        page[26] = (segTable.size and 0xFF).toByte()
        segTable.copyInto(page, 27)
        payload.copyInto(page, 27 + segTable.size)
        val crc = computeOggCrc(page)
        writeInt32LEToArray(page, 22, crc)
        return page
    }

    private fun computeOggCrc(page: ByteArray): Int {
        var crc = 0
        for (b in page) {
            val idx = ((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF
            crc = (crc shl 8) xor OGG_CRC_TABLE[idx]
        }
        return crc
    }

    // =========================================================================
    // WAV (RIFF LIST/INFO + embedded ID3v2.3 "id3 " chunk)
    // =========================================================================

    private fun rewriteWavBytes(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ): ByteArray? {
        if (bytes.size < 12 || !startsWithAscii(bytes, 0, "RIFF") || !startsWithAscii(bytes, 8, "WAVE")) {
            return null
        }
        val body = ByteArrayOutputStream(bytes.size)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val cid = String(bytes, pos, 4, StandardCharsets.US_ASCII)
            val cSize = readInt32LE(bytes, pos + 4).coerceAtLeast(0)
            val paddedEnd = (pos + 8 + cSize + (cSize and 1)).coerceAtMost(bytes.size)
            if (cid != "LIST" && !cid.equals("id3 ", ignoreCase = true)) {
                body.write(bytes, pos, (pos + 8 + cSize).coerceAtMost(bytes.size) - pos)
                if ((cSize and 1) == 1) body.write(0)
            }
            if (paddedEnd <= pos) break
            pos = paddedEnd
        }

        // Write LIST/INFO chunk
        val infoItems = ByteArrayOutputStream().apply {
            write("INFO".toByteArray(StandardCharsets.US_ASCII))
            if (title.isNotEmpty()) writeRiffSubChunk(this, "INAM", title)
            if (artist.isNotEmpty()) writeRiffSubChunk(this, "IART", artist)
            if (album.isNotEmpty()) writeRiffSubChunk(this, "IPRD", album)
        }.toByteArray()
        body.write("LIST".toByteArray(StandardCharsets.US_ASCII))
        writeInt32LE(body, infoItems.size)
        body.write(infoItems)
        if ((infoItems.size and 1) == 1) body.write(0)

        // Write "id3 " chunk with full ID3v2.3 + cover art
        val id3TagOnly = rewriteMp3Id3v23(ByteArray(0), title, artist, album, coverBytes).let { full ->
            // Strip trailing 128-byte ID3v1 footer for RIFF id3 chunk
            if (full.size >= 128 && startsWithAscii(full, full.size - 128, "TAG")) {
                full.copyOfRange(0, full.size - 128)
            } else full
        }
        body.write("id3 ".toByteArray(StandardCharsets.US_ASCII))
        writeInt32LE(body, id3TagOnly.size)
        body.write(id3TagOnly)
        if ((id3TagOnly.size and 1) == 1) body.write(0)

        val bodyBytes = body.toByteArray()
        val out = ByteArrayOutputStream(12 + bodyBytes.size)
        out.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
        writeInt32LE(out, 4 + bodyBytes.size)
        out.write("WAVE".toByteArray(StandardCharsets.US_ASCII))
        out.write(bodyBytes)
        return out.toByteArray()
    }

    private fun parseWavMetadata(bytes: ByteArray): AudioFileMetadataReadResult? {
        if (bytes.size < 12) return null
        var pos = 12
        var title = ""
        var artist = ""
        var album = ""
        var cover: ByteArray? = null
        while (pos + 8 <= bytes.size) {
            val cid = String(bytes, pos, 4, StandardCharsets.US_ASCII)
            val cSize = readInt32LE(bytes, pos + 4).coerceAtLeast(0)
            val dataStart = pos + 8
            val dataEnd = (dataStart + cSize).coerceAtMost(bytes.size)
            if (cid.equals("id3 ", ignoreCase = true) && dataEnd > dataStart + 10) {
                parseId3v2Metadata(bytes, dataStart)?.let { parsed ->
                    if (parsed.title.isNotBlank()) title = parsed.title
                    if (parsed.artist.isNotBlank()) artist = parsed.artist
                    if (parsed.album.isNotBlank()) album = parsed.album
                    if (cover == null) cover = parsed.embeddedCoverBytes
                }
            } else if (cid == "LIST" && dataStart + 4 <= dataEnd && startsWithAscii(bytes, dataStart, "INFO")) {
                var sub = dataStart + 4
                while (sub + 8 <= dataEnd) {
                    val sid = String(bytes, sub, 4, StandardCharsets.US_ASCII)
                    val sSize = readInt32LE(bytes, sub + 4).coerceAtLeast(0)
                    val sEnd = (sub + 8 + sSize).coerceAtMost(dataEnd)
                    val valStr = String(bytes, sub + 8, sEnd - (sub + 8), StandardCharsets.UTF_8).trim('\u0000', ' ')
                    when (sid) {
                        "INAM" -> if (title.isBlank()) title = valStr
                        "IART" -> if (artist.isBlank()) artist = valStr
                        "IPRD" -> if (album.isBlank()) album = valStr
                    }
                    sub += 8 + sSize + (sSize and 1)
                }
            }
            val next = pos + 8 + cSize + (cSize and 1)
            if (next <= pos) break
            pos = next
        }
        return AudioFileMetadataReadResult(title, artist, album, cover != null, cover)
    }

    private fun writeRiffSubChunk(out: ByteArrayOutputStream, id: String, value: String) {
        val raw = (value + "\u0000").toByteArray(StandardCharsets.UTF_8)
        out.write(id.toByteArray(StandardCharsets.US_ASCII))
        writeInt32LE(out, raw.size)
        out.write(raw)
        if ((raw.size and 1) == 1) out.write(0)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun isPngBytes(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
    }

    private fun isMp3Sync(bytes: ByteArray): Boolean {
        return bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xE0) == 0xE0
    }

    private fun startsWithAscii(bytes: ByteArray, offset: Int, ascii: String): Boolean {
        if (offset < 0 || offset + ascii.length > bytes.size) return false
        for (i in ascii.indices) {
            if (bytes[offset + i].toInt().toChar() != ascii[i]) return false
        }
        return true
    }

    private fun readSynchsafeInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun writeSynchsafeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 21) and 0x7F)
        out.write((value ushr 14) and 0x7F)
        out.write((value ushr 7) and 0x7F)
        out.write(value and 0x7F)
    }

    private fun readInt32BE(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun writeInt32BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeInt32LE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeInt32LEToArray(arr: ByteArray, offset: Int, value: Int) {
        arr[offset] = (value and 0xFF).toByte()
        arr[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        arr[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        arr[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
