package com.example

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.AudioFileMetadataRepository
import com.example.data.AudioFileMetadataUpdate
import com.example.data.LauncherPreferencesRepository
import com.example.data.MusicCatalogAndLyricsRepository
import com.example.model.AlbumExpectedTrack
import com.example.model.AudioCaptureState
import com.example.model.EqualizerState
import com.example.model.LocalAudioTrack
import com.example.model.MediaPlayerState
import com.example.model.MediaSessionUi
import com.example.model.MusicDrawerTab
import com.example.model.MusicSourceKind
import com.example.model.PlayerCoverStyle
import com.example.model.PowerampTab
import com.example.model.PreviewPlaybackState
import com.example.model.ShizukuState
import com.example.model.SmartAlbum
import com.example.model.SourceSelectionMode
import com.example.model.SyncedLyricLine
import com.example.model.TrackLyricsData
import com.example.model.TrackSourceCandidate
import com.example.model.UnifiedMusicSearchResult
import com.example.model.ZipArchiveKind
import com.example.model.ZipUpdateInspection
import com.example.ui.components.AppTileGrid
import com.example.ui.components.EditAudioMetadataDialog
import com.example.ui.components.InlineMusicControlDrawer
import com.example.ui.components.PowerampAlbumsTab
import com.example.ui.components.PowerampPlayerScreen
import com.example.ui.components.SourceChooserDialog
import com.example.ui.components.ZipUpdateDialog
import com.example.ui.theme.KeyDeckTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlbumsSearchPreviewAndCoversTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `smart albums and track cover preferences persist atomically without onStart race`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = LauncherPreferencesRepository(app)

        runBlocking {
            val created = SmartAlbum(
                id = "alb_test_1",
                title = "Black Holes and Revelations",
                artist = "Muse",
                expectedTrackCount = 2,
                expectedTracks = listOf(
                    AlbumExpectedTrack(1, "Starlight", "Muse", 240_000L),
                    AlbumExpectedTrack(2, "Supermassive Black Hole", "Muse", 209_000L)
                )
            )
            repo.updateSmartAlbums { listOf(created) }
            val loaded = repo.getSmartAlbumsNow()
            assertEquals(1, loaded.size)
            assertEquals("Black Holes and Revelations", loaded.first().title)

            val keys = MusicCatalogAndLyricsRepository.buildTrackCoverLookupKeys(
                trackId = "trk_99",
                title = "Starlight",
                artist = "Muse"
            )
            repo.saveTrackCoverOverride(keys, "/tmp/custom_starlight.jpg")
            val overrides = repo.getTrackCoverOverridesNow()
            assertEquals(
                "/tmp/custom_starlight.jpg",
                MusicCatalogAndLyricsRepository.resolveTrackCoverPath(overrides, "trk_99", "Starlight", "Muse")
            )

            repo.clearTrackCoverOverride(keys)
            val cleared = repo.getTrackCoverOverridesNow()
            assertNull(MusicCatalogAndLyricsRepository.resolveTrackCoverPath(cleared, "trk_99", "Starlight", "Muse"))

            repo.setDefaultTrackFallbackCoverPath("/tmp/vinyl_loop.mp4")
            val savedFallback = kotlinx.coroutines.withTimeout(2000L) {
                repo.defaultTrackFallbackCoverPath.first { it == "/tmp/vinyl_loop.mp4" }
            }
            assertEquals("/tmp/vinyl_loop.mp4", savedFallback)
            repo.setDefaultTrackFallbackCoverPath(null)
        }
    }

    @Test
    fun `search results rank exact query matches first and reject 30s previews`() {
        val candidates = listOf(
            TrackSourceCandidate(
                id = "wrong_remix",
                sourceKind = MusicSourceKind.ARCHIVE_LOSSY,
                sourceLabel = "Archive.org MP3",
                title = "Night Drive Remix Part 2",
                artist = "Someone Else",
                durationMs = 210_000L,
                downloadUrl = "https://example.com/other.mp3"
            ),
            TrackSourceCandidate(
                id = "exact_match",
                sourceKind = MusicSourceKind.STREAMING_PAID,
                sourceLabel = "Яндекс Музыка",
                title = "Starlight",
                artist = "Muse",
                durationMs = 240_000L,
                packageName = "ru.yandex.music"
            )
        )

        val grouped = MusicCatalogAndLyricsRepository.groupAndSortCandidates(
            candidates = candidates,
            streamingApps = emptyList(),
            query = "Muse Starlight"
        )
        assertTrue(grouped.isNotEmpty())
        assertEquals("Starlight", grouped.first().title)
        assertEquals("Muse", grouped.first().artist)

        // 30-second clip must be rejected when full duration is ~4 minutes or unknown
        assertFalse(MusicCatalogAndLyricsRepository.isDurationCompatible(240_000L, 30_000L))
        assertFalse(MusicCatalogAndLyricsRepository.isDurationCompatible(0L, 29_500L))
        assertTrue(MusicCatalogAndLyricsRepository.isDurationCompatible(240_000L, 232_000L))
        assertTrue(MusicCatalogAndLyricsRepository.isDurationCompatible(0L, 215_000L))
    }

    @Test
    fun `deezer album and audius track parsers extract expected tracks and covers`() {
        val deezerJson = """
            {
              "id": 302127,
              "title": "Discovery",
              "nb_tracks": 2,
              "cover_xl": "https://cdn.example.com/discovery_xl.jpg",
              "artist": { "name": "Daft Punk" },
              "tracks": {
                "data": [
                  { "track_position": 1, "title": "One More Time", "duration": 320, "artist": { "name": "Daft Punk" } },
                  { "track_position": 2, "title": "Aerodynamic", "duration": 212, "artist": { "name": "Daft Punk" } }
                ]
              }
            }
        """.trimIndent()
        val album = MusicCatalogAndLyricsRepository.parseDeezerAlbumJson(deezerJson)
        assertNotNull(album)
        assertEquals("Discovery", album!!.title)
        assertEquals("Daft Punk", album.artist)
        assertEquals(2, album.expectedTracks.size)
        assertEquals(320_000L, album.expectedTracks[0].durationMs)
        assertEquals("https://cdn.example.com/discovery_xl.jpg", album.coverUrl)

        val audiusJson = """
            {
              "data": [
                {
                  "id": "track_abc",
                  "title": "Starlight",
                  "duration": 241,
                  "user": { "name": "Muse Tribute" },
                  "artwork": { "480x480": "https://cdn.example.com/audius_cover.jpg" }
                }
              ]
            }
        """.trimIndent()
        val audiusTracks = MusicCatalogAndLyricsRepository.parseAudiusSearchJson(audiusJson)
        assertEquals(1, audiusTracks.size)
        assertEquals("Starlight", audiusTracks.first().title)
        assertEquals(241_000L, audiusTracks.first().durationMs)
        assertTrue(audiusTracks.first().downloadUrl!!.contains("/v1/tracks/track_abc/stream"))
    }

    @Test
    fun `source chooser dialog displays inline preview button and status banner`() {
        val cand = TrackSourceCandidate(
            id = "preview_cand_1",
            sourceKind = MusicSourceKind.ARCHIVE_LOSSY,
            sourceLabel = "Audius",
            title = "Starlight",
            artist = "Muse",
            durationMs = 240_000L,
            downloadUrl = "https://discoveryprovider.audius.co/v1/tracks/1/stream",
            previewUrl = "https://discoveryprovider.audius.co/v1/tracks/1/stream"
        )
        val result = UnifiedMusicSearchResult(
            key = "muse|starlight",
            title = "Starlight",
            artist = "Muse",
            album = "Black Holes",
            durationMs = 240_000L,
            candidates = listOf(cand)
        )
        var previewToggled = false
        composeTestRule.setContent {
            KeyDeckTheme {
                SourceChooserDialog(
                    result = result,
                    forDownload = true,
                    previewState = PreviewPlaybackState(
                        candidateId = "preview_cand_1",
                        isPlaying = true
                    ),
                    onTogglePreview = { previewToggled = true },
                    onPickPlay = {},
                    onPickDownload = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("source_chooser_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("source_preview_status_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("source_preview_btn_0").assertIsDisplayed().performClick()
        assertTrue(previewToggled)
    }

    @Test
    fun `poweramp albums tab and attach local tracks dialog handle track additions cleanly`() {
        val album = SmartAlbum(
            id = "alb_1",
            title = "Discovery",
            artist = "Daft Punk",
            expectedTrackCount = 1,
            expectedTracks = listOf(AlbumExpectedTrack(1, "One More Time", "Daft Punk", 320_000L)),
            statusMessage = "Треклист получен"
        )
        val localTracks = listOf(
            LocalAudioTrack(
                id = "loc_1",
                title = "One More Time",
                artist = "Daft Punk",
                album = "Discovery",
                durationMs = 320_000L,
                uriString = "file:///music/one_more_time.flac",
                filePath = "/music/one_more_time.flac",
                formatLabel = "FLAC",
                bitrateKbps = 1411
            ),
            LocalAudioTrack(
                id = "loc_1",
                title = "One More Time",
                artist = "Daft Punk",
                album = "Discovery",
                durationMs = 320_000L,
                uriString = "file:///music/one_more_time.flac",
                filePath = "/music/one_more_time.flac",
                formatLabel = "FLAC",
                bitrateKbps = 1411
            )
        )
        composeTestRule.setContent {
            KeyDeckTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 700.dp)) {
                    PowerampAlbumsTab(
                        albums = listOf(album),
                        selectedAlbumId = "alb_1",
                        localTracks = localTracks,
                        onSelectAlbum = {},
                        onCreateAlbum = { _, _ -> },
                        onRefreshAlbumOnline = {},
                        onAttachLocalTracks = { _, _ -> },
                        onDetachLocalTrack = { _, _ -> },
                        onPickCustomAlbumCover = {},
                        onSelectAlternateCoverUrl = { _, _ -> },
                        onDeleteAlbum = {},
                        onPlayLocalTrack = {},
                        onOpenLyricsForTrack = {},
                        onDownloadMissingTrack = { _, _ -> },
                        onDownloadAllMissingTracks = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("poweramp_albums_tab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_add_local_tracks_btn").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("attach_album_tracks_dialog").assertIsDisplayed()
    }

    @Test
    fun `zip update dialog surfaces signature mismatch and unknown sources prompt`() {
        val inspection = ZipUpdateInspection(
            sourceUriString = "file:///sdcard/keydeck_27.apk",
            archiveName = "keydeck_27.apk",
            archiveSizeBytes = 15_000_000L,
            kind = ZipArchiveKind.APK_UPDATE,
            isSamePackageUpdate = true,
            targetPackageName = "com.aistudio.keydeck.vqmzpt",
            targetVersionName = "2.7",
            targetVersionCode = 3L,
            currentVersionName = "2.6",
            currentVersionCode = 2L,
            extractedApkPaths = listOf("/tmp/keydeck_27.apk"),
            signatureMatchesInstalled = false,
            installedSignatureSha256 = "AABBCCDD",
            targetSignatureSha256 = "11223344",
            canRequestInstalls = false,
            summaryMessage = "Готов APK для обновления"
        )
        var openedUnknownSources = false
        composeTestRule.setContent {
            KeyDeckTheme {
                ZipUpdateDialog(
                    currentVersionName = "2.6",
                    currentVersionCode = 2L,
                    inspection = inspection,
                    isBusy = false,
                    statusMessage = null,
                    shizukuState = ShizukuState(),
                    onPickZipFile = {},
                    onExportBackupZip = {},
                    onApplyUpdate = {},
                    onOpenUnknownAppSources = { openedUnknownSources = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("zip_signature_mismatch_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("zip_open_unknown_sources_btn").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(openedUnknownSources)
    }

    @Test
    fun `reconcileAlbumTracks stays idempotent across repeated runs and ms_audio vs fs aliases`() {
        val rawAlbum = SmartAlbum(
            id = "alb_dedup",
            title = "Discovery",
            artist = "Daft Punk",
            expectedTrackCount = 3,
            expectedTracks = listOf(
                AlbumExpectedTrack(1, "One More Time", "Daft Punk", 320_000L),
                AlbumExpectedTrack(1, "One More Time", "Daft Punk", 320_000L),
                AlbumExpectedTrack(2, "Aerodynamic", "Daft Punk", 212_000L)
            ),
            attachedLocalTrackIds = listOf("ms_audio:101", "fs:/music/one_more_time.flac")
        )
        val localTracks = listOf(
            LocalAudioTrack(
                id = "ms_audio:101",
                title = "One More Time",
                artist = "Daft Punk",
                album = "Discovery",
                durationMs = 320_000L,
                uriString = "content://media/external/audio/media/101",
                filePath = "/music/one_more_time.flac",
                formatLabel = "FLAC",
                bitrateKbps = 1411
            ),
            LocalAudioTrack(
                id = "fs:/music/one_more_time.flac",
                title = "One More Time",
                artist = "Daft Punk",
                album = "Discovery",
                durationMs = 320_000L,
                uriString = "file:///music/one_more_time.flac",
                filePath = "/music/one_more_time.flac",
                formatLabel = "FLAC",
                bitrateKbps = 1411
            )
        )

        var current = rawAlbum
        repeat(5) {
            current = MusicCatalogAndLyricsRepository.reconcileAlbumTracks(current, localTracks)
        }

        assertEquals(2, current.expectedTracks.size)
        assertEquals(1, current.matchedCount)
        assertEquals(1, current.missingCount)
        assertEquals(1, current.attachedLocalTrackIds.size)
        assertEquals("ms_audio:101", current.attachedLocalTrackIds.first())
    }

    @Test
    fun `internet audio catalog defines at least 15 sources prioritizing FLAC and Discogs`() {
        assertTrue(MusicCatalogAndLyricsRepository.internetAudioSources.size >= 15)
        assertTrue(
            MusicCatalogAndLyricsRepository.internetAudioSources.any {
                it.domain == "discogs.com" && it.supportsFlac
            }
        )

        val discogsSearchJson = """
            {
              "results": [
                {
                  "id": 249504,
                  "title": "Daft Punk - Discovery",
                  "format": ["Vinyl", "FLAC"],
                  "cover_image": "https://img.discogs.com/cover.jpg"
                }
              ]
            }
        """.trimIndent()
        val discogsSrch = MusicCatalogAndLyricsRepository.parseDiscogsSearchJson(discogsSearchJson)
        assertEquals(1, discogsSrch.size)
        assertEquals(MusicSourceKind.DISCOGS_CATALOG, discogsSrch.first().second.sourceKind)
        assertEquals("flac", discogsSrch.first().second.fileExtension)

        val openverseJson = """
            {
              "results": [
                {
                  "id": "ov1",
                  "title": "Aerodynamic",
                  "creator": "Daft Punk",
                  "url": "https://cdn.example.org/aero.flac",
                  "duration": 212000,
                  "filetype": "flac"
                }
              ]
            }
        """.trimIndent()
        val openverse = MusicCatalogAndLyricsRepository.parseOpenverseAudioJson(openverseJson)
        assertEquals(1, openverse.size)
        assertEquals(MusicSourceKind.ARCHIVE_FLAC, openverse.first().sourceKind)

        val hearthisJson = """
            [{"id":"99","title":"Digital Love","duration":"298","stream_url":"https://hearthis.at/stream/99","user":{"username":"Daft"}}]
        """.trimIndent()
        assertEquals(1, MusicCatalogAndLyricsRepository.parseHearthisSearchJson(hearthisJson).size)

        val ccMixterJson = """
            [{"upload_id":77,"upload_name":"Crescendolls","user_name":"Daft","files":[{"download_url":"https://ccmixter.org/files/77.flac","file_format_info":{"format_name":"flac"}}]}]
        """.trimIndent()
        val cc = MusicCatalogAndLyricsRepository.parseCcMixterJson(ccMixterJson)
        assertEquals(1, cc.size)
        assertEquals(MusicSourceKind.ARCHIVE_FLAC, cc.first().sourceKind)

        val sorted = MusicCatalogAndLyricsRepository.groupAndSortCandidates(
            candidates = listOf(
                TrackSourceCandidate(
                    id = "lossy_1",
                    sourceKind = MusicSourceKind.ARCHIVE_LOSSY,
                    sourceLabel = "Archive MP3",
                    title = "Aerodynamic",
                    artist = "Daft Punk",
                    durationMs = 212_000L,
                    downloadUrl = "https://example.com/a.mp3"
                ),
                TrackSourceCandidate(
                    id = "discogs_1",
                    sourceKind = MusicSourceKind.DISCOGS_CATALOG,
                    sourceLabel = "Discogs.com",
                    title = "Aerodynamic",
                    artist = "Daft Punk",
                    durationMs = 212_000L,
                    fileExtension = "flac"
                ),
                TrackSourceCandidate(
                    id = "flac_1",
                    sourceKind = MusicSourceKind.ARCHIVE_FLAC,
                    sourceLabel = "Archive.org FLAC",
                    title = "Aerodynamic",
                    artist = "Daft Punk",
                    durationMs = 212_000L,
                    downloadUrl = "https://example.com/a.flac",
                    fileExtension = "flac"
                )
            ),
            streamingApps = emptyList(),
            query = "Daft Punk Aerodynamic"
        )
        assertEquals(1, sorted.size)
        assertEquals(MusicSourceKind.ARCHIVE_FLAC, sorted.first().candidates[0].sourceKind)
        assertEquals(MusicSourceKind.DISCOGS_CATALOG, sorted.first().candidates[1].sourceKind)
        assertEquals(MusicSourceKind.ARCHIVE_LOSSY, sorted.first().candidates[2].sourceKind)
    }

    @Test
    fun `poweramp albums tab renders cover grid and single-button missing-tracks toggle`() {
        val album = SmartAlbum(
            id = "alb_grid_1",
            title = "Discovery",
            artist = "Daft Punk",
            expectedTrackCount = 2,
            expectedTracks = listOf(
                AlbumExpectedTrack(1, "One More Time", "Daft Punk", 320_000L, matchedLocalTrackId = "loc_1"),
                AlbumExpectedTrack(2, "Missing Track Song", "Daft Punk", 212_000L, matchedLocalTrackId = null)
            )
        )
        var selectedId by mutableStateOf<String?>(null)
        composeTestRule.setContent {
            KeyDeckTheme {
                Box(modifier = Modifier.size(width = 420.dp, height = 760.dp)) {
                    PowerampAlbumsTab(
                        albums = listOf(album),
                        selectedAlbumId = selectedId,
                        localTracks = listOf(
                            LocalAudioTrack(
                                id = "loc_1",
                                title = "One More Time",
                                artist = "Daft Punk",
                                album = "Discovery",
                                durationMs = 320_000L,
                                uriString = "file:///music/one_more_time.flac",
                                filePath = "/music/one_more_time.flac",
                                formatLabel = "FLAC"
                            )
                        ),
                        onSelectAlbum = { selectedId = it },
                        onCreateAlbum = { _, _ -> },
                        onRefreshAlbumOnline = {},
                        onAttachLocalTracks = { _, _ -> },
                        onDetachLocalTrack = { _, _ -> },
                        onPickCustomAlbumCover = {},
                        onSelectAlternateCoverUrl = { _, _ -> },
                        onDeleteAlbum = {},
                        onPlayLocalTrack = {},
                        onOpenLyricsForTrack = {},
                        onDownloadMissingTrack = { _, _ -> },
                        onDownloadAllMissingTracks = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("smart_albums_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smart_album_card_alb_grid_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Missing Track Song").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_toggle_missing_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Missing Track Song").assertCountEquals(0)
    }

    @Test
    fun `compact drawer exposes albums tab`() {
        val album = SmartAlbum(
            id = "alb_compact_1",
            title = "Random Access Memories",
            artist = "Daft Punk",
            expectedTrackCount = 1,
            expectedTracks = listOf(AlbumExpectedTrack(1, "Instant Crush", "Daft Punk", 337_000L))
        )
        composeTestRule.setContent {
            KeyDeckTheme {
                Box(modifier = Modifier.size(width = 420.dp, height = 760.dp)) {
                    var drawerTab by remember { mutableStateOf(MusicDrawerTab.STREAMING) }
                    InlineMusicControlDrawer(
                        mediaState = MediaPlayerState(),
                        livePositionMs = 0L,
                        selectedTab = drawerTab,
                        localTracks = emptyList(),
                        telegramNotifications = emptyList(),
                        trackSearchQuery = "",
                        hasAudioReadAccess = true,
                        hasAllFilesAccess = true,
                        musicApps = emptyList(),
                        isLandscape = false,
                        smartAlbums = listOf(album),
                        selectedAlbumId = null,
                        onSelectTab = { drawerTab = it },
                        onTrackSearchChange = {},
                        onSkipToQueueItem = {},
                        onInvokeCustomAction = {},
                        onPlayBrowserTrack = {},
                        onSearchAndPlayInStream = { _, _ -> },
                        onConnectBrowser = {},
                        onPlayLocalTrack = {},
                        onOpenHubNotification = {},
                        onRefreshTracks = {},
                        onRequestAudioPermission = {},
                        onRequestAllFilesAccess = {},
                        onClose = {},
                        onSelectSession = {},
                        onTogglePlayPause = {},
                        onSkipPrevious = {},
                        onSkipNext = {},
                        onSeekTo = {},
                        onSeekBy = {},
                        onOpenActivePlayer = {},
                        onSetVolume = {},
                        onToggleMute = {},
                        onOpenNotificationAccess = {},
                        onLaunchMusicApp = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("music_tab_albums").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_albums_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_album_card_alb_compact_1").assertIsDisplayed()
    }

    @Test
    fun `global search opens matching album`() {
        val album = SmartAlbum(
            id = "alb_compact_1",
            title = "Random Access Memories",
            artist = "Daft Punk",
            expectedTrackCount = 1,
            expectedTracks = listOf(AlbumExpectedTrack(1, "Instant Crush", "Daft Punk", 337_000L))
        )
        var openedFromGlobalSearch: SmartAlbum? = null
        composeTestRule.setContent {
            KeyDeckTheme {
                val gridState = rememberLazyGridState()
                val focusRequester = remember { FocusRequester() }
                AppTileGrid(
                    apps = emptyList(),
                    searchImages = emptyList(),
                    searchFiles = emptyList(),
                    albumSearchResults = listOf(album),
                    isSearchingDevice = false,
                    indexedFolderCount = 0,
                    hasAllFilesAccess = true,
                    columns = 3,
                    searchQuery = "Random Access",
                    focusedIndex = 0,
                    highlightedLetter = null,
                    isLoading = false,
                    gridState = gridState,
                    searchFocusRequester = focusRequester,
                    isSearchFocused = false,
                    onSearchFocusChange = {},
                    onRequestSearchFocus = {},
                    onSearchQueryChange = {},
                    onClearSearch = {},
                    onLaunchApp = {},
                    onLongPressApp = {},
                    onOpenSearchFile = {},
                    onLongPressSearchFile = {},
                    onOpenSmartAlbum = { openedFromGlobalSearch = it },
                    onConnectSearchFolder = {},
                    onRequestAllFilesAccess = {},
                    onSearchWeb = { _, _ -> },
                    onOpenShortcuts = {},
                    onOpenWriter = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_search_album_alb_compact_1").assertIsDisplayed().performClick()
        assertEquals("alb_compact_1", openedFromGlobalSearch?.id)
    }

    @Test
    fun `lyrics confirmation checkmark toggles and persists primary flag`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = LauncherPreferencesRepository(app)
        runBlocking {
            val savedLyrics = TrackLyricsData(
                trackKey = "daft punk|instant crush",
                title = "Instant Crush",
                artist = "Daft Punk",
                plainText = "I didn't want to be the one to forget",
                sourceLabel = "LRCLIB",
                isConfirmedPrimary = true
            )
            repo.saveLyricsOverride(savedLyrics)
            val loaded = repo.getLyricsOverridesNow()[savedLyrics.trackKey]
            assertNotNull(loaded)
            assertTrue(loaded!!.isConfirmedPrimary)
        }

        var confirmToggled = false
        val session = MediaSessionUi(
            id = "local_test",
            packageName = "com.example",
            appLabel = "KeyDeck",
            title = "Instant Crush",
            artist = "Daft Punk",
            album = "RAM",
            isPlaying = false,
            positionMs = 0L,
            durationMs = 337_000L,
            playbackSpeed = 1.0f,
            lastUpdateElapsedMs = 0L
        )
        composeTestRule.setContent {
            KeyDeckTheme {
                PowerampPlayerScreen(
                    mediaState = MediaPlayerState(sessions = listOf(session), selectedSessionId = session.id),
                    livePositionMs = 0L,
                    coverStyle = PlayerCoverStyle.COVER,
                    activeTab = PowerampTab.LYRICS,
                    isLandscape = false,
                    sourceSelectionMode = SourceSelectionMode.AUTO,
                    localTracks = emptyList(),
                    unifiedSearchQuery = "",
                    unifiedSearchResults = emptyList(),
                    isSearchingOnline = false,
                    currentLyrics = TrackLyricsData(
                        trackKey = "daft punk|instant crush",
                        title = "Instant Crush",
                        artist = "Daft Punk",
                        syncedLines = listOf(SyncedLyricLine(0L, "Test line")),
                        plainText = "Test line",
                        isConfirmedPrimary = false
                    ),
                    isFetchingLyrics = false,
                    equalizerState = EqualizerState(),
                    audioCaptureState = AudioCaptureState(),
                    smartAlbums = emptyList(),
                    selectedAlbumId = null,
                    onClose = {},
                    onSelectTab = {},
                    onToggleSourceMode = {},
                    onSelectSession = {},
                    onTogglePlayPause = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onSeekTo = {},
                    onSeekBy = {},
                    onSkipToQueueItem = {},
                    onOpenActivePlayer = {},
                    onPlayLocalTrack = {},
                    onDeleteLocalTrack = {},
                    onDeleteActiveTrack = {},
                    onSetPlaybackSpeed = {},
                    onCycleRepeatMode = {},
                    onToggleShuffle = {},
                    onSearchMusicEverywhere = {},
                    onUnifiedTrackAction = { _, _ -> },
                    onOpenSourcePicker = { _, _ -> },
                    onStopAudioCapture = {},
                    onAutoFetchLyrics = {},
                    onSaveLyrics = {},
                    onDeleteLyrics = {},
                    onToggleConfirmPrimaryLyrics = { confirmToggled = true },
                    onSetEqualizerEnabled = {},
                    onApplyEqualizerPreset = {},
                    onSetEqualizerBand = { _, _ -> },
                    onSetBassBoost = {},
                    onSetVirtualizer = {},
                    onSelectAlbum = {},
                    onCreateAlbum = { _, _ -> },
                    onRefreshAlbumOnline = {},
                    onAttachLocalTracksToAlbum = { _, _ -> },
                    onDetachLocalTrackFromAlbum = { _, _ -> },
                    onPickCustomAlbumCover = {},
                    onSelectAlternateCoverUrl = { _, _ -> },
                    onDeleteAlbum = {},
                    onDownloadMissingAlbumTrack = { _, _ -> },
                    onDownloadAllMissingAlbumTracks = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("lyrics_confirm_primary_btn").assertIsDisplayed().performClick()
        assertTrue(confirmToggled)
    }

    @Test
    fun `strict matching rejects partial substring titles and prioritizes exact album artist matches`() {
        assertFalse(MusicCatalogAndLyricsRepository.titlesMatchStrict("Осколки", "Хрустальные осколки"))
        assertTrue(MusicCatalogAndLyricsRepository.titlesMatchStrict("08. Осколки (2002)", "Осколки"))
        assertTrue(MusicCatalogAndLyricsRepository.artistsMatch("АукцЫон", "Аукцион"))
        assertFalse(MusicCatalogAndLyricsRepository.artistsMatch("АукцЫон", "Леонид Фёдоров и Волков"))

        val wrongTrack = LocalAudioTrack(
            id = "loc_wrong",
            title = "Хрустальные осколки",
            artist = "Другой Исполнитель",
            album = "Чужой альбом",
            durationMs = 240_000L,
            uriString = "file:///music/wrong.mp3",
            filePath = "/music/wrong.mp3",
            formatLabel = "MP3"
        )
        val exactTrack = LocalAudioTrack(
            id = "loc_exact",
            title = "Осколки",
            artist = "АукцЫон",
            album = "Это мама",
            durationMs = 430_000L,
            uriString = "file:///music/oskolki.mp3",
            filePath = "/music/oskolki.mp3",
            formatLabel = "MP3"
        )
        val album = SmartAlbum(
            id = "alb_auktyon",
            title = "Это мама",
            artist = "АукцЫон",
            expectedTrackCount = 1,
            expectedTracks = listOf(AlbumExpectedTrack(8, "Осколки", "АукцЫон", 430_000L))
        )

        val wrongOnlyReconciled = MusicCatalogAndLyricsRepository.reconcileAlbumTracks(album, listOf(wrongTrack))
        assertNull(wrongOnlyReconciled.expectedTracks.first().matchedLocalTrackId)

        val bothReconciled = MusicCatalogAndLyricsRepository.reconcileAlbumTracks(album, listOf(wrongTrack, exactTrack))
        assertEquals("loc_exact", bothReconciled.expectedTracks.first().matchedLocalTrackId)

        val wrongCandidate = TrackSourceCandidate(
            id = "cand_wrong",
            title = "Хрустальные осколки",
            artist = "Другой",
            sourceKind = MusicSourceKind.ARCHIVE_LOSSY,
            sourceLabel = "Archive MP3"
        )
        val exactCandidate = TrackSourceCandidate(
            id = "cand_exact",
            title = "Осколки",
            artist = "Аукцион",
            album = "Это мама",
            sourceKind = MusicSourceKind.ARCHIVE_FLAC,
            sourceLabel = "Archive FLAC",
            durationMs = 428_000L
        )
        assertFalse(
            MusicCatalogAndLyricsRepository.isDownloadCandidateAccurate(
                candidate = wrongCandidate,
                expectedTitle = "Осколки",
                expectedArtist = "АукцЫон",
                expectedAlbum = "Это мама",
                expectedDurationMs = 430_000L
            )
        )
        assertTrue(
            MusicCatalogAndLyricsRepository.isDownloadCandidateAccurate(
                candidate = exactCandidate,
                expectedTitle = "Осколки",
                expectedArtist = "АукцЫон",
                expectedAlbum = "Это мама",
                expectedDurationMs = 430_000L
            )
        )
    }

    @Test
    fun `audio metadata writer updates mp3 and flac tags and cover inside binary file`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tempDir = File(app.cacheDir, "meta_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val dummyAudioFrame = ByteArray(256) { ((it * 7) and 0xFF).toByte() }.apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xFB.toByte()
            this[2] = 0x90.toByte()
            this[3] = 0x00.toByte()
        }
        val mp3File = File(tempDir, "test_track.mp3").apply { writeBytes(dummyAudioFrame) }
        val coverBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4, 0xFF.toByte(), 0xD9.toByte())

        val mp3Track = LocalAudioTrack(
            id = "fs:${mp3File.absolutePath}",
            title = "Old Title",
            artist = "Old Artist",
            album = "Old Album",
            durationMs = 120_000L,
            uriString = Uri.fromFile(mp3File).toString(),
            filePath = mp3File.absolutePath,
            formatLabel = "MP3"
        )
        val mp3Res = AudioFileMetadataRepository.writeTrackMetadata(
            context = app,
            track = mp3Track,
            update = AudioFileMetadataUpdate(
                title = "Осколки",
                artist = "АукцЫон",
                album = "Это мама",
                newCoverBytes = coverBytes
            )
        )
        assertTrue(mp3Res.isSuccess)
        val parsedMp3 = AudioFileMetadataRepository.parseBinaryMetadata(mp3File.readBytes())
        assertNotNull(parsedMp3)
        assertEquals("Осколки", parsedMp3!!.title)
        assertEquals("АукцЫон", parsedMp3.artist)
        assertEquals("Это мама", parsedMp3.album)
        assertArrayEquals(coverBytes, parsedMp3.embeddedCoverBytes)
        // Audio frames appended intact right before the 128-byte ID3v1 TAG footer
        val mp3Bytes = mp3File.readBytes()
        val id3v1Offset = mp3Bytes.size - 128
        assertTrue(id3v1Offset >= dummyAudioFrame.size)
        assertEquals("TAG", String(mp3Bytes, id3v1Offset, 3, Charsets.US_ASCII))
        assertArrayEquals(dummyAudioFrame, mp3Bytes.copyOfRange(id3v1Offset - dummyAudioFrame.size, id3v1Offset))

        // Build minimal FLAC (fLaC + 34-byte last STREAMINFO block + audio bytes)
        val flacHeader = ByteArray(42)
        flacHeader[0] = 'f'.code.toByte()
        flacHeader[1] = 'L'.code.toByte()
        flacHeader[2] = 'a'.code.toByte()
        flacHeader[3] = 'C'.code.toByte()
        flacHeader[4] = 0x80.toByte() // isLast=1, type=0 (STREAMINFO)
        flacHeader[5] = 0
        flacHeader[6] = 0
        flacHeader[7] = 34
        val flacFrames = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x69, 0x00, 11, 22, 33, 44)
        val flacFile = File(tempDir, "test_track.flac").apply { writeBytes(flacHeader + flacFrames) }
        val flacTrack = LocalAudioTrack(
            id = "fs:${flacFile.absolutePath}",
            title = "Old FLAC",
            artist = "",
            album = "",
            durationMs = 200_000L,
            uriString = Uri.fromFile(flacFile).toString(),
            filePath = flacFile.absolutePath,
            formatLabel = "FLAC"
        )
        val flacRes = AudioFileMetadataRepository.writeTrackMetadata(
            context = app,
            track = flacTrack,
            update = AudioFileMetadataUpdate(
                title = "Зима",
                artist = "АукцЫон",
                album = "Это мама",
                newCoverBytes = coverBytes
            )
        )
        assertTrue(flacRes.isSuccess)
        val parsedFlac = AudioFileMetadataRepository.parseBinaryMetadata(flacFile.readBytes())
        assertNotNull(parsedFlac)
        assertEquals("Зима", parsedFlac!!.title)
        assertEquals("АукцЫон", parsedFlac.artist)
        assertEquals("Это мама", parsedFlac.album)
        assertArrayEquals(coverBytes, parsedFlac.embeddedCoverBytes)
        val flacBytes = flacFile.readBytes()
        assertArrayEquals(flacFrames, flacBytes.copyOfRange(flacBytes.size - flacFrames.size, flacBytes.size))
        tempDir.deleteRecursively()
        Unit
    }

    @Test
    fun `album track long press opens context menu and metadata editor submits updated tags`() {
        val localTrack = LocalAudioTrack(
            id = "loc_oskolki",
            title = "Осколки",
            artist = "АукцЫон",
            album = "Это мама",
            durationMs = 430_000L,
            uriString = "file:///music/oskolki.flac",
            filePath = "/music/oskolki.flac",
            formatLabel = "FLAC"
        )
        val album = SmartAlbum(
            id = "alb_eto_mama",
            title = "Это мама",
            artist = "АукцЫон",
            expectedTrackCount = 1,
            expectedTracks = listOf(
                AlbumExpectedTrack(8, "Осколки", "АукцЫон", 430_000L, matchedLocalTrackId = "loc_oskolki", matchedFormat = "FLAC")
            )
        )
        var editingTrack by mutableStateOf<LocalAudioTrack?>(null)
        var savedTuple by mutableStateOf<Triple<String, String, String>?>(null)

        composeTestRule.setContent {
            KeyDeckTheme {
                Box(modifier = Modifier.size(width = 420.dp, height = 760.dp)) {
                    PowerampAlbumsTab(
                        albums = listOf(album),
                        selectedAlbumId = "alb_eto_mama",
                        localTracks = listOf(localTrack),
                        onSelectAlbum = {},
                        onCreateAlbum = { _, _ -> },
                        onRefreshAlbumOnline = {},
                        onAttachLocalTracks = { _, _ -> },
                        onDetachLocalTrack = { _, _ -> },
                        onPickCustomAlbumCover = {},
                        onSelectAlternateCoverUrl = { _, _ -> },
                        onDeleteAlbum = {},
                        onPlayLocalTrack = {},
                        onOpenLyricsForTrack = {},
                        onDownloadMissingTrack = { _, _ -> },
                        onDownloadAllMissingTracks = {},
                        onEditTrackMetadata = { editingTrack = it }
                    )
                    editingTrack?.let { tr ->
                        EditAudioMetadataDialog(
                            track = tr,
                            onPickCoverImage = {},
                            onDismiss = { editingTrack = null },
                            onSave = { title, artist, alb, _ ->
                                savedTuple = Triple(title, artist, alb)
                                editingTrack = null
                            }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("album_track_row_8").assertIsDisplayed().performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("track_context_menu_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("track_ctx_delete_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("track_ctx_edit_metadata_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("edit_audio_metadata_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("track_meta_title_input").performScrollTo().performTextReplacement("Осколки (Remaster)")
        composeTestRule.onNodeWithTag("track_meta_artist_input").performScrollTo().performTextReplacement("АукцЫон")
        composeTestRule.onNodeWithTag("track_meta_album_input").performScrollTo().performTextReplacement("Это мама (2002)")
        composeTestRule.onNodeWithTag("track_meta_save_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(Triple("Осколки (Remaster)", "АукцЫон", "Это мама (2002)"), savedTuple)
    }
}
