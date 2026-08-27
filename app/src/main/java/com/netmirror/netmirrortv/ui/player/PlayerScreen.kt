package com.netmirror.netmirrortv.ui.player

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.util.TypedValue
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Format
import android.util.Log
import java.io.File
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.netmirror.netmirrortv.util.TokenStore
import com.netmirror.netmirrortv.utils.PlaybackProgressStore
import com.netmirror.netmirrortv.utils.PlayerPreferences
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
private object PlayerCacheManager {
    private const val MAX_CACHE_SIZE: Long = 512 * 1024 * 1024 // 512MB LRU cache
    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.cacheDir, "media3_hls_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCache = it
                }
            }
        }
    }
}

enum class MenuTab {
    AUDIO, SUBTITLE, RESOLUTION, SPEED
}

enum class PlayerFocusArea {
    PLAY_PAUSE, MENU_TABS, MENU_CONTENT
}

data class TrackOption(
    val id: String,
    val label: String,
    val isSelected: Boolean,
    val onSelect: () -> Unit
)

val GoldAccent = Color(0xFFFFD700)

@Composable
fun PlayerScreen(
    id: String,
    mainId: String = "",
    type: String = "",
    viewModel: PlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(id) {
        viewModel.loadStream(id)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val s = uiState) {
            is PlayerUiState.Loading -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is PlayerUiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = s.message,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadStream(id, force = true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
            is PlayerUiState.Success -> {
                val streamUrl = s.response.playUrl.orEmpty()
                val title = s.response.displayTitle.orEmpty()
                val referer = s.response.referer?.takeIf { it.isNotBlank() } ?: "https://net52.cc"

                if (streamUrl.isNotBlank()) {
                    PlayerContent(
                        streamUrl = streamUrl,
                        title = title,
                        referer = referer,
                        id = id,
                        mainId = mainId,
                        type = type,
                        onThirtySecondsPlayed = {
                            viewModel.logRecentPlay(id = id, mainId = mainId, type = type)
                        }
                    )
                } else {
                    Text(
                        text = "No stream available.",
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    streamUrl: String,
    title: String,
    referer: String = "https://net52.cc",
    id: String = "",
    mainId: String = "",
    type: String = "",
    onThirtySecondsPlayed: () -> Unit = {}
) {
    val context = LocalContext.current
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(false)
            )
        }
    }

    val exoPlayer = remember(streamUrl, referer) {
        val userToken = TokenStore.getUserToken(context).orEmpty()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setConnectTimeoutMs(15_000)
            setReadTimeoutMs(20_000)
            setAllowCrossProtocolRedirects(true)
            setKeepPostFor302Redirects(true)
            setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0")
            setDefaultRequestProperties(
                buildMap {
                    put("Referer", referer)
                    put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0")
                    put("X-Requested-With", "NetmirrorNewTV v1.0")
                }
            )
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(PlayerCacheManager.getCache(context))
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20_000,
                60_000,
                2_500,
                5_000
            )
            .setBackBuffer(15_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val defaultDecoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                // Prioritize universal software decoders (c2.android / OMX.google) for 100% compatibility across all Android TV devices
                defaultDecoders.sortedByDescending { codecInfo ->
                    codecInfo.softwareOnly ||
                    !codecInfo.hardwareAccelerated ||
                    codecInfo.name.startsWith("c2.android.", ignoreCase = true) ||
                    codecInfo.name.startsWith("OMX.google.", ignoreCase = true)
                }
            }
        }

        val hlsMediaSourceFactory = HlsMediaSource.Factory(cacheDataSourceFactory)
            .setAllowChunklessPreparation(true)

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val hlsMediaSource = hlsMediaSourceFactory.createMediaSource(mediaItem)

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                addAnalyticsListener(object : AnalyticsListener {
                    override fun onDroppedVideoFrames(
                        eventTime: AnalyticsListener.EventTime,
                        droppedFrames: Int,
                        elapsedMs: Long
                    ) {
                        if (droppedFrames > 15) {
                            Log.w("PlayerScreen", "Dropped $droppedFrames frames in ${elapsedMs}ms")
                        }
                    }

                    override fun onPlayerError(
                        eventTime: AnalyticsListener.EventTime,
                        error: androidx.media3.common.PlaybackException
                    ) {
                        Log.e("PlayerScreen", "Player playback error: ${error.errorCodeName} (${error.errorCode})", error)
                    }
                })
                setMediaSource(hlsMediaSource)
                prepare()
                playWhenReady = true
            }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }

    var controlsVisible by remember { mutableStateOf(true) }
    var isMenuVisible by remember { mutableStateOf(false) }

    var selectedQualityLabel by remember { mutableStateOf("1080p") }
    var selectedSubtitleLabel by remember { mutableStateOf("Off") }
    var selectedAudioLabel by remember { mutableStateOf("Hindi") }
    var selectedSpeedLabel by remember { mutableStateOf("1.0x") }
    var initialPreferencesSet by remember { mutableStateOf(false) }

    // Exit Toast Dialog State
    var showExitToast by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Focus & Navigation State
    var activeFocusArea by remember { mutableStateOf(PlayerFocusArea.PLAY_PAUSE) }
    var activeMenuTab by remember { mutableStateOf(MenuTab.AUDIO) }

    // Fast seeking state
    var isFastSeeking by remember { mutableStateOf(false) }
    var showSeekOverlay by remember { mutableStateOf(false) }
    var fastSeekTargetPos by remember { mutableLongStateOf(0L) }
    var fastSeekAccumulator by remember { mutableLongStateOf(0L) }
    var seekHoldStartTime by remember { mutableLongStateOf(0L) }
    var lastSeekDirection by remember { mutableStateOf(0) } // -1 for Left, 1 for Right

    // Auto-dismiss seek pop-up card 1.5 seconds after key release
    LaunchedEffect(isFastSeeking) {
        if (isFastSeeking) {
            showSeekOverlay = true
        } else if (showSeekOverlay) {
            delay(1500L)
            showSeekOverlay = false
            fastSeekAccumulator = 0L
        }
    }

    // Focus Requesters
    val playPauseFocusRequester = remember { FocusRequester() }
    val tabAudioFocusRequester = remember { FocusRequester() }
    val tabSubtitleFocusRequester = remember { FocusRequester() }
    val tabResolutionFocusRequester = remember { FocusRequester() }
    val tabSpeedFocusRequester = remember { FocusRequester() }
    val contentFirstOptionFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    // Default Focus on Center Play/Pause button on launch
    LaunchedEffect(Unit) {
        delay(200)
        runCatching {
            playPauseFocusRequester.requestFocus()
            activeFocusArea = PlayerFocusArea.PLAY_PAUSE
        }
    }

    // Always focus Play/Pause button immediately when controls become visible OR when buffering finishes!
    LaunchedEffect(controlsVisible, isMenuVisible, isBuffering) {
        if (controlsVisible && !isMenuVisible && !isBuffering) {
            delay(50)
            runCatching {
                playPauseFocusRequester.requestFocus()
                activeFocusArea = PlayerFocusArea.PLAY_PAUSE
            }
        }
    }

    // When menu opens, set active tab to AUDIO and focus directly on the Audio button
    LaunchedEffect(isMenuVisible) {
        if (isMenuVisible) {
            activeMenuTab = MenuTab.AUDIO
            activeFocusArea = PlayerFocusArea.MENU_TABS
            delay(50)
            runCatching {
                tabAudioFocusRequester.requestFocus()
            }
        }
    }

    // Auto-dismiss Exit Toast after 2 seconds
    LaunchedEffect(showExitToast, lastBackPressTime) {
        if (showExitToast) {
            delay(2000L)
            showExitToast = false
        }
    }

    // Restore saved user preferences & apply initial track selection logic
    LaunchedEffect(exoPlayer) {
        if (!initialPreferencesSet && exoPlayer.currentTracks.groups.isNotEmpty()) {
            applyInitialPreferences(
                context = context,
                exoPlayer = exoPlayer,
                trackSelector = trackSelector,
                onQualityUpdated = { selectedQualityLabel = it },
                onAudioUpdated = { selectedAudioLabel = it },
                onSubtitleUpdated = { selectedSubtitleLabel = it }
            )
            initialPreferencesSet = true
        }
    }

    // 30-second playback trigger state (resets when stream or id changes)
    var thirtySecondTriggered by remember(streamUrl, id) { mutableStateOf(false) }

    // Read saved progress & parse resume position
    val savedProgress = remember(id) { PlaybackProgressStore.getProgress(context, id) }
    val resumeAtMs = remember(savedProgress) { PlaybackProgressStore.parseResumePositionMs(savedProgress) }
    var hasResumed by remember(streamUrl, id) { mutableStateOf(false) }
    var metaLoaded by remember { mutableStateOf(false) }

    /* ③ seek + resume once BOTH flags are true ---------------------------- */
    LaunchedEffect(metaLoaded, resumeAtMs) {
        if (!metaLoaded || resumeAtMs <= 0L || hasResumed) return@LaunchedEffect

        // Ensure resume position is not at the very end of video (> duration - 5s)
        if (resumeAtMs < (exoPlayer.duration - 5000L).coerceAtLeast(0L)) {
            Log.d("PlayerScreen", "Resuming playback from stored position: ${resumeAtMs}ms (${formatTime(resumeAtMs)}) for ID: $id")
            exoPlayer.seekTo(resumeAtMs)
            exoPlayer.play()
        }
        hasResumed = true
    }

    // Trigger API & log Video ID after 30 seconds of playback
    LaunchedEffect(thirtySecondTriggered) {
        if (thirtySecondTriggered) {
            val calculatedVideoId = if (type.equals("t", ignoreCase = true)) "SE_${mainId.ifBlank { id }}" else mainId.ifBlank { id }
            Log.d("PlayerScreen", "30 seconds playback reached! Calculated Videoid: '$calculatedVideoId' (playId='$id', mainId='$mainId', type='$type'). Running recentPlay API...")
            onThirtySecondsPlayed()
        }
    }

    // Save progress every 3 seconds once 30 seconds have been played
    LaunchedEffect(thirtySecondTriggered, id) {
        if (!thirtySecondTriggered) return@LaunchedEffect

        while (true) {
            delay(3000L)
            if (exoPlayer.currentPosition > 0L && exoPlayer.duration > 0L) {
                val curSec = exoPlayer.currentPosition / 1000L
                val durSec = exoPlayer.duration / 1000L
                PlaybackProgressStore.saveProgress(context, id, curSec, durSec)
                Log.d("PlayerProgress", "hellloooooooooooooooo -> progress_$id = $curSec:$durSec")
            }
        }
    }

    // Store episode ID and title for series if type is 't'
    LaunchedEffect(thirtySecondTriggered, type, mainId, id, title) {
        if (!thirtySecondTriggered) return@LaunchedEffect

        if (type.equals("t", ignoreCase = true)) {
            val sid = mainId.ifBlank { id }
            PlaybackProgressStore.saveSeriesLastEpisode(context, sid, id)
            if (title.isNotBlank()) {
                PlaybackProgressStore.saveSeriesResumeTitle(context, sid, title)
            }
            Log.d("PlayerProgress", "Stored episode ID and resume title for series: progress_$sid = $id, title = $title")
        }
    }

    // Auto focus rootFocusRequester when controls auto-hide so remote key events are intercepted
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            delay(100)
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Periodically update current video position and buffer status
    LaunchedEffect(isPlaying) {
        while (true) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying

            if (!metaLoaded && exoPlayer.duration > 0L) {
                metaLoaded = true
            }

            // Fire 30-second trigger once current playback position hits 30 seconds (30,000ms)
            if (!thirtySecondTriggered && currentPosition >= 30_000L) {
                thirtySecondTriggered = true
            }

            delay(400L)
        }
    }

    // Auto-hide controls after 8 seconds of inactivity if menu is NOT open
    LaunchedEffect(controlsVisible, isMenuVisible, activeFocusArea, isFastSeeking, showSeekOverlay) {
        if (controlsVisible && !isMenuVisible && activeFocusArea == PlayerFocusArea.PLAY_PAUSE && !isFastSeeking && !showSeekOverlay) {
            delay(8000L)
            controlsVisible = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                playerViewRef?.post {
                    playerViewRef?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    playerViewRef?.videoSurfaceView?.requestLayout()
                    playerViewRef?.requestLayout()
                    playerViewRef?.invalidate()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                duration = exoPlayer.duration.coerceAtLeast(0L)
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    if (exoPlayer.duration > 0L) {
                        metaLoaded = true
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (!initialPreferencesSet) {
                    applyInitialPreferences(
                        context = context,
                        exoPlayer = exoPlayer,
                        trackSelector = trackSelector,
                        onQualityUpdated = { selectedQualityLabel = it },
                        onAudioUpdated = { selectedAudioLabel = it },
                        onSubtitleUpdated = { selectedSubtitleLabel = it }
                    )
                    initialPreferencesSet = true
                }
                if (exoPlayer.duration > 0L) {
                    metaLoaded = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            // Save progress once when component unmounts
            if (thirtySecondTriggered && exoPlayer.currentPosition > 0L && exoPlayer.duration > 0L) {
                val curSec = exoPlayer.currentPosition / 1000L
                val durSec = exoPlayer.duration / 1000L
                PlaybackProgressStore.saveProgress(context, id, curSec, durSec)
                Log.d("PlayerProgress", "current time on unmount: ${curSec}s (progress_$id = $curSec:$durSec)")
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back -> {
                            if (isMenuVisible) {
                                isMenuVisible = false
                                activeFocusArea = PlayerFocusArea.PLAY_PAUSE
                                runCatching { playPauseFocusRequester.requestFocus() }
                                return@onPreviewKeyEvent true
                            } else {
                                val now = System.currentTimeMillis()
                                if (showExitToast && (now - lastBackPressTime < 2000L)) {
                                    // User pressed back again within 2 seconds -> Navigate back one step
                                    if (onBackPressedDispatcher != null) {
                                        onBackPressedDispatcher.onBackPressed()
                                    } else {
                                        (context as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                                    }
                                    return@onPreviewKeyEvent true
                                } else {
                                    // Show toast in top right corner
                                    showExitToast = true
                                    lastBackPressTime = now
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                        Key.DirectionDown -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                isMenuVisible = false
                                activeFocusArea = PlayerFocusArea.PLAY_PAUSE
                                runCatching { playPauseFocusRequester.requestFocus() }
                                return@onPreviewKeyEvent true
                            } else if (!isMenuVisible) {
                                isMenuVisible = true
                                activeMenuTab = MenuTab.AUDIO
                                activeFocusArea = PlayerFocusArea.MENU_TABS
                                runCatching { tabAudioFocusRequester.requestFocus() }
                                return@onPreviewKeyEvent true
                            } else if (isMenuVisible && activeFocusArea == PlayerFocusArea.MENU_TABS) {
                                activeFocusArea = PlayerFocusArea.MENU_CONTENT
                                runCatching { contentFirstOptionFocusRequester.requestFocus() }
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionUp -> {
                            if (controlsVisible && isMenuVisible) {
                                if (activeFocusArea == PlayerFocusArea.MENU_CONTENT) {
                                    activeFocusArea = PlayerFocusArea.MENU_TABS
                                    val req = when (activeMenuTab) {
                                        MenuTab.AUDIO -> tabAudioFocusRequester
                                        MenuTab.SUBTITLE -> tabSubtitleFocusRequester
                                        MenuTab.RESOLUTION -> tabResolutionFocusRequester
                                        MenuTab.SPEED -> tabSpeedFocusRequester
                                    }
                                    runCatching { req.requestFocus() }
                                    return@onPreviewKeyEvent true
                                } else if (activeFocusArea == PlayerFocusArea.MENU_TABS) {
                                    isMenuVisible = false
                                    activeFocusArea = PlayerFocusArea.PLAY_PAUSE
                                    runCatching { playPauseFocusRequester.requestFocus() }
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                        Key.DirectionLeft -> {
                            if (!controlsVisible || !isMenuVisible || activeFocusArea == PlayerFocusArea.PLAY_PAUSE) {
                                controlsVisible = true
                                activeFocusArea = PlayerFocusArea.PLAY_PAUSE
                                runCatching { playPauseFocusRequester.requestFocus() }
                                val maxDur = exoPlayer.duration.coerceAtLeast(0L)
                                val now = System.currentTimeMillis()

                                // Reset acceleration timer if starting fresh seek or switching direction
                                if (!isFastSeeking || lastSeekDirection != -1) {
                                    seekHoldStartTime = now
                                    lastSeekDirection = -1
                                }

                                val holdDuration = now - seekHoldStartTime
                                val step = calculateSeekStep(holdDuration, maxDur)

                                val startPos = if (isFastSeeking) fastSeekTargetPos else exoPlayer.currentPosition
                                val newTarget = (startPos - step).coerceAtLeast(0L)
                                isFastSeeking = true
                                fastSeekTargetPos = newTarget
                                fastSeekAccumulator -= step
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionRight -> {
                            if (!controlsVisible || !isMenuVisible || activeFocusArea == PlayerFocusArea.PLAY_PAUSE) {
                                controlsVisible = true
                                activeFocusArea = PlayerFocusArea.PLAY_PAUSE
                                runCatching { playPauseFocusRequester.requestFocus() }
                                val maxDur = exoPlayer.duration.coerceAtLeast(0L)
                                val now = System.currentTimeMillis()

                                // Reset acceleration timer if starting fresh seek or switching direction
                                if (!isFastSeeking || lastSeekDirection != 1) {
                                    seekHoldStartTime = now
                                    lastSeekDirection = 1
                                }

                                val holdDuration = now - seekHoldStartTime
                                val step = calculateSeekStep(holdDuration, maxDur)

                                val startPos = if (isFastSeeking) fastSeekTargetPos else exoPlayer.currentPosition
                                val newTarget = (startPos + step).coerceAtMost(maxDur)
                                isFastSeeking = true
                                fastSeekTargetPos = newTarget
                                fastSeekAccumulator += step
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                    if ((event.key == Key.DirectionLeft || event.key == Key.DirectionRight) && isFastSeeking) {
                        exoPlayer.seekTo(fastSeekTargetPos)
                        isFastSeeking = false
                        seekHoldStartTime = 0L
                        lastSeekDirection = 0
                        runCatching { playPauseFocusRequester.requestFocus() }
                        return@onPreviewKeyEvent true
                    }
                }

                // Single-press OK/Enter key when controls are hidden toggles play/pause AND focuses Play/Pause button immediately
                if (!controlsVisible && event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                    controlsVisible = true
                    isMenuVisible = false
                    activeFocusArea = PlayerFocusArea.PLAY_PAUSE
                    runCatching { playPauseFocusRequester.requestFocus() }
                    return@onPreviewKeyEvent true
                }

                false
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.BLACK,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null
                            )
                        )
                        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    }
                    playerViewRef = this
                }
            },
            update = { view ->
                view.player = exoPlayer
            }
        )

        // Premium Top Right Corner Exit Toast Dialog
        AnimatedVisibility(
            visible = showExitToast,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Please press back again to exit",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Top Title Header with smooth gradient scrim
        AnimatedVisibility(
            visible = controlsVisible && title.isNotBlank(),
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.9f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(top = 28.dp, bottom = 44.dp, start = 40.dp, end = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Buffering Indicator (Clean Circular Spinner without text)
        if (isBuffering && !showSeekOverlay) {
            CircularProgressIndicator(
                color = Color(0xFFE50914),
                trackColor = Color.White.copy(alpha = 0.2f),
                strokeWidth = 3.5.dp,
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
            )
        }

        // Center Controls: Fixed Center Play/Pause with Pop-up Seek Card Overlay on Top
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // Center Play/Pause Button
            AnimatedVisibility(
                visible = controlsVisible && !isBuffering,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250))
            ) {
                CenterPlayButton(
                    isPlaying = isPlaying,
                    focusRequester = playPauseFocusRequester,
                    onFocusGained = { activeFocusArea = PlayerFocusArea.PLAY_PAUSE },
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                )
            }

            // Seek Pop-up Card (Overlaid upon Buffering & Play/Pause with subtle opacity, auto-hides after 1.5s)
            AnimatedVisibility(
                visible = showSeekOverlay,
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.88f, animationSpec = tween(150)),
                exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.88f, animationSpec = tween(250))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141414).copy(alpha = 0.88f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 26.dp, vertical = 14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Centered Forward / Backward Logo
                        Icon(
                            imageVector = if (fastSeekAccumulator < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                            contentDescription = if (fastSeekAccumulator < 0) "Rewind" else "Fast Forward",
                            tint = Color(0xFFE50914),
                            modifier = Modifier.size(34.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        // Current Seek Time / Total Time
                        Text(
                            text = "${formatTime(fastSeekTargetPos)} / ${formatTime(duration)}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Progress Line Bar with Gradient Scrim (when Bottom Menu is NOT open)
        AnimatedVisibility(
            visible = controlsVisible && !isMenuVisible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 40.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayPosition = if (showSeekOverlay || isFastSeeking) fastSeekTargetPos else currentPosition
                    val progress = if (duration > 0) (displayPosition.toFloat() / duration.toFloat()) else 0f
                    val bufferProgress = if (duration > 0) (bufferedPosition.toFloat() / duration.toFloat()) else 0f

                    // Left Side: Current / Seek Position Time
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatTime(displayPosition),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Middle: Ultra-Smooth Glitch-Free Canvas Seekbar
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .height(20.dp)
                    ) {
                        val barHeight = 5.dp.toPx()
                        val cornerRadius = CornerRadius(barHeight / 2, barHeight / 2)
                        val centerY = size.height / 2
                        val barTop = centerY - (barHeight / 2)

                        // 1. Background Track
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.22f),
                            topLeft = Offset(0f, barTop),
                            size = Size(size.width, barHeight),
                            cornerRadius = cornerRadius
                        )

                        // 2. Buffer Progress Track
                        if (bufferProgress > 0f) {
                            val bufferWidth = (size.width * bufferProgress.coerceIn(0f, 1f)).coerceAtLeast(0f)
                            if (bufferWidth > 0f) {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.45f),
                                    topLeft = Offset(0f, barTop),
                                    size = Size(bufferWidth, barHeight),
                                    cornerRadius = cornerRadius
                                )
                            }
                        }

                        // 3. Played Progress Track with Gradient
                        val playedWidth = (size.width * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)
                        if (playedWidth > 0f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFFF3B30), Color(0xFFE50914)),
                                    startX = 0f,
                                    endX = size.width.coerceAtLeast(1f)
                                ),
                                topLeft = Offset(0f, barTop),
                                size = Size(playedWidth, barHeight),
                                cornerRadius = cornerRadius
                            )
                        }

                        // 4. Scrubber Thumb
                        if (progress > 0f) {
                            val thumbRadius = 6.5.dp.toPx()
                            val thumbX = playedWidth.coerceIn(thumbRadius, size.width - thumbRadius)

                            // Outer Red Ring
                            drawCircle(
                                color = Color(0xFFE50914),
                                radius = thumbRadius,
                                center = Offset(thumbX, centerY)
                            )
                            // Inner White Center
                            drawCircle(
                                color = Color.White,
                                radius = thumbRadius - 2.2.dp.toPx(),
                                center = Offset(thumbX, centerY)
                            )
                        }
                    }

                    // Right Side: Total Duration Time
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatTime(duration),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom Overlay Menu Panel (Solid Black Background, Seekbar REMOVED)
        AnimatedVisibility(
            visible = controlsVisible && isMenuVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 40.dp, vertical = 24.dp)
            ) {
                // Row 1: Separate Menu Tabs (Audio, Subtitle, Resolution, Playback Speed)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    MenuTabPill(
                        title = "Audio",
                        badge = selectedAudioLabel,
                        isSelected = activeMenuTab == MenuTab.AUDIO,
                        focusRequester = tabAudioFocusRequester,
                        onFocusGained = {
                            activeFocusArea = PlayerFocusArea.MENU_TABS
                            activeMenuTab = MenuTab.AUDIO
                        },
                        onClick = {
                            activeMenuTab = MenuTab.AUDIO
                        }
                    )

                    MenuTabPill(
                        title = "Subtitle",
                        badge = selectedSubtitleLabel,
                        isSelected = activeMenuTab == MenuTab.SUBTITLE,
                        focusRequester = tabSubtitleFocusRequester,
                        onFocusGained = {
                            activeFocusArea = PlayerFocusArea.MENU_TABS
                            activeMenuTab = MenuTab.SUBTITLE
                        },
                        onClick = {
                            activeMenuTab = MenuTab.SUBTITLE
                        }
                    )

                    MenuTabPill(
                        title = "Resolution",
                        badge = selectedQualityLabel,
                        isSelected = activeMenuTab == MenuTab.RESOLUTION,
                        focusRequester = tabResolutionFocusRequester,
                        onFocusGained = {
                            activeFocusArea = PlayerFocusArea.MENU_TABS
                            activeMenuTab = MenuTab.RESOLUTION
                        },
                        onClick = {
                            activeMenuTab = MenuTab.RESOLUTION
                        }
                    )

                    MenuTabPill(
                        title = "Playback Speed",
                        badge = selectedSpeedLabel,
                        isSelected = activeMenuTab == MenuTab.SPEED,
                        focusRequester = tabSpeedFocusRequester,
                        onFocusGained = {
                            activeFocusArea = PlayerFocusArea.MENU_TABS
                            activeMenuTab = MenuTab.SPEED
                        },
                        onClick = {
                            activeMenuTab = MenuTab.SPEED
                        }
                    )
                }

                // LazyRow in Menu Content for all tabs
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (activeMenuTab) {
                        MenuTab.AUDIO -> {
                            val audioOptions = remember(exoPlayer.currentTracks, selectedAudioLabel) {
                                getAudioOptions(context, exoPlayer, trackSelector) {
                                    selectedAudioLabel = it
                                }
                            }

                            Column {
                                Text(
                                    text = "Audio",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(audioOptions) { index, option ->
                                        OptionContentPill(
                                            label = option.label,
                                            isSelected = option.isSelected,
                                            focusRequester = if (index == 0) contentFirstOptionFocusRequester else null,
                                            onFocusGained = { activeFocusArea = PlayerFocusArea.MENU_CONTENT },
                                            onClick = { option.onSelect() }
                                        )
                                    }
                                }
                            }
                        }

                        MenuTab.SUBTITLE -> {
                            val subtitleOptions = remember(exoPlayer.currentTracks, selectedSubtitleLabel) {
                                getSubtitleOptions(context, exoPlayer, trackSelector) {
                                    selectedSubtitleLabel = it
                                }
                            }

                            Column {
                                Text(
                                    text = "Subtitle",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(subtitleOptions) { index, option ->
                                        OptionContentPill(
                                            label = option.label,
                                            isSelected = option.isSelected,
                                            focusRequester = if (index == 0) contentFirstOptionFocusRequester else null,
                                            onFocusGained = { activeFocusArea = PlayerFocusArea.MENU_CONTENT },
                                            onClick = { option.onSelect() }
                                        )
                                    }
                                }
                            }
                        }

                        MenuTab.RESOLUTION -> {
                            val qualityOptions = remember(exoPlayer.currentTracks, selectedQualityLabel) {
                                getQualityOptions(context, exoPlayer, trackSelector) {
                                    selectedQualityLabel = it
                                }
                            }

                            Column {
                                Text(
                                    text = "Resolution",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(qualityOptions) { index, option ->
                                        OptionContentPill(
                                            label = option.label,
                                            isSelected = option.isSelected,
                                            focusRequester = if (index == 0) contentFirstOptionFocusRequester else null,
                                            onFocusGained = { activeFocusArea = PlayerFocusArea.MENU_CONTENT },
                                            onClick = { option.onSelect() }
                                        )
                                    }
                                }
                            }
                        }

                        MenuTab.SPEED -> {
                            val speedOptions = remember(selectedSpeedLabel) {
                                getSpeedOptions(exoPlayer, selectedSpeedLabel) {
                                    selectedSpeedLabel = it
                                }
                            }

                            Column {
                                Text(
                                    text = "Playback Speed",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(speedOptions) { index, option ->
                                        OptionContentPill(
                                            label = option.label,
                                            isSelected = option.isSelected,
                                            focusRequester = if (index == 0) contentFirstOptionFocusRequester else null,
                                            onFocusGained = { activeFocusArea = PlayerFocusArea.MENU_CONTENT },
                                            onClick = { option.onSelect() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CenterPlayButton(
    isPlaying: Boolean,
    focusRequester: FocusRequester,
    onFocusGained: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.14f else 1.0f,
        animationSpec = tween(150),
        label = "play_btn_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocusGained()
            }
            .scale(scale)
            .size(68.dp)
            .clip(CircleShape)
            .background(
                color = if (isFocused) Color.White else Color(0xFF1E1E1E).copy(alpha = 0.85f),
                shape = CircleShape
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.5.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier
                .size(38.dp)
                .then(
                    if (!isPlaying) Modifier.padding(start = 3.dp) else Modifier
                )
        )
    }
}

// When focus leaves tab buttons (Audio, Subtitle, Resolution, Speed), set background to transparent and text color to white
@Composable
private fun MenuTabPill(
    title: String,
    badge: String?,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onFocusGained: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = if (isFocused) Color.White else Color.Transparent
    val textColor = if (isFocused) Color.Black else Color.White
    val badgeColor = if (isFocused) Color.DarkGray else Color.White.copy(alpha = 0.8f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocusGained()
            }
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        if (!badge.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = badge,
                color = badgeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun OptionContentPill(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    onFocusGained: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val requester = focusRequester ?: remember { FocusRequester() }

    val pillBgColor = when {
        isFocused -> Color.White
        isSelected -> Color(0xFF333333)
        else -> Color(0xFF222222)
    }

    val textColor = when {
        isFocused -> Color.Black
        isSelected -> GoldAccent
        else -> Color.White.copy(alpha = 0.9f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocusGained()
            }
            .width(110.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(pillBgColor)
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun getLanguageDisplayName(language: String?): String? {
    if (language.isNullOrBlank() || language.equals("und", ignoreCase = true)) return null
    return try {
        val locale = Locale.forLanguageTag(language)
        val display = locale.getDisplayLanguage(Locale.ENGLISH)
        if (display.isNotBlank()) {
            display.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        } else null
    } catch (_: Exception) {
        null
    }
}

private fun getTrackDisplayLabel(format: Format, defaultPrefix: String, index: Int): String {
    if (!format.label.isNullOrBlank()) return format.label!!
    val langDisplay = getLanguageDisplayName(format.language)
    if (langDisplay != null) return langDisplay
    val langCode = format.language?.uppercase()
    if (!langCode.isNullOrBlank() && langCode != "UND") return langCode
    return "$defaultPrefix ${index + 1}"
}

@OptIn(UnstableApi::class)
private fun applyInitialPreferences(
    context: Context,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    onQualityUpdated: (String) -> Unit,
    onAudioUpdated: (String) -> Unit,
    onSubtitleUpdated: (String) -> Unit
) {
    val tracks = exoPlayer.currentTracks
    if (tracks.groups.isEmpty()) return

    // 1. Initial Quality Selection Logic (Saved Pref -> 1080p -> 720p -> Top/Highest)
    val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    val savedQuality = PlayerPreferences.getPreferredQuality(context)

    data class QualityCandidate(val height: Int, val label: String, val group: Tracks.Group, val index: Int)
    val qualityCandidates = mutableListOf<QualityCandidate>()

    for (group in videoGroups) {
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            if (format.height > 0) {
                val label = if (format.height == 720) "HD 720P" else "${format.height}p"
                qualityCandidates.add(QualityCandidate(format.height, label, group, i))
            }
        }
    }

    if (qualityCandidates.isNotEmpty()) {
        val targetQuality = qualityCandidates.firstOrNull { it.label.equals(savedQuality, ignoreCase = true) }
            ?: qualityCandidates.firstOrNull { it.height == 1080 }
            ?: qualityCandidates.firstOrNull { it.height == 720 }
            ?: qualityCandidates.maxByOrNull { it.height }

        targetQuality?.let { candidate ->
            val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .addOverride(TrackSelectionOverride(candidate.group.mediaTrackGroup, candidate.index))
                .build()
            exoPlayer.trackSelectionParameters = newParams
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setOverrideForType(TrackSelectionOverride(candidate.group.mediaTrackGroup, candidate.index))
                .build()
            onQualityUpdated(candidate.label)
        }
    }

    // 2. Initial Audio Selection Logic (Saved Pref -> Hindi -> English -> Top/First)
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val savedAudio = PlayerPreferences.getPreferredAudio(context)

    data class AudioCandidate(val label: String, val langCode: String?, val displayLang: String?, val group: Tracks.Group, val index: Int)
    val audioCandidates = mutableListOf<AudioCandidate>()

    for (group in audioGroups) {
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val label = getTrackDisplayLabel(format, "Audio", i)
            val langCode = format.language
            val displayLang = getLanguageDisplayName(format.language)
            audioCandidates.add(AudioCandidate(label, langCode, displayLang, group, i))
        }
    }

    if (audioCandidates.isNotEmpty()) {
        val targetAudio = audioCandidates.firstOrNull { candidate ->
            savedAudio != null && (
                candidate.label.equals(savedAudio, ignoreCase = true) ||
                candidate.langCode.equals(savedAudio, ignoreCase = true) ||
                candidate.displayLang.equals(savedAudio, ignoreCase = true)
            )
        }
            ?: audioCandidates.firstOrNull { candidate ->
                candidate.label.contains("Hindi", ignoreCase = true) ||
                candidate.langCode.equals("hi", ignoreCase = true) ||
                candidate.langCode.equals("hin", ignoreCase = true) ||
                candidate.displayLang.equals("Hindi", ignoreCase = true)
            }
            ?: audioCandidates.firstOrNull { candidate ->
                candidate.label.contains("English", ignoreCase = true) ||
                candidate.langCode.equals("en", ignoreCase = true) ||
                candidate.langCode.equals("eng", ignoreCase = true) ||
                candidate.displayLang.equals("English", ignoreCase = true)
            }
            ?: audioCandidates.firstOrNull()

        targetAudio?.let { candidate ->
            val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(TrackSelectionOverride(candidate.group.mediaTrackGroup, candidate.index))
                .build()
            exoPlayer.trackSelectionParameters = newParams
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setOverrideForType(TrackSelectionOverride(candidate.group.mediaTrackGroup, candidate.index))
                .build()
            onAudioUpdated(candidate.label)
        }
    }

    // 3. Initial Subtitle Selection Logic (Saved Pref -> Default to "Off")
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val savedSubtitle = PlayerPreferences.getPreferredSubtitle(context)

    if (savedSubtitle != null && !savedSubtitle.equals("Off", ignoreCase = true) && textGroups.isNotEmpty()) {
        data class TextCandidate(val label: String, val langCode: String?, val displayLang: String?, val group: Tracks.Group, val index: Int)
        val textCandidates = mutableListOf<TextCandidate>()
        for (group in textGroups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val label = getTrackDisplayLabel(format, "Subtitle", i)
                val langCode = format.language
                val displayLang = getLanguageDisplayName(format.language)
                textCandidates.add(TextCandidate(label, langCode, displayLang, group, i))
            }
        }
        val targetSub = textCandidates.firstOrNull { candidate ->
            candidate.label.equals(savedSubtitle, ignoreCase = true) ||
            candidate.langCode.equals(savedSubtitle, ignoreCase = true) ||
            candidate.displayLang.equals(savedSubtitle, ignoreCase = true)
        }
        if (targetSub != null) {
            val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .addOverride(TrackSelectionOverride(targetSub.group.mediaTrackGroup, targetSub.index))
                .build()
            exoPlayer.trackSelectionParameters = newParams
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(targetSub.group.mediaTrackGroup, targetSub.index))
                .build()
            onSubtitleUpdated(targetSub.label)
        } else {
            val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            exoPlayer.trackSelectionParameters = newParams
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            onSubtitleUpdated("Off")
        }
    } else {
        val newParams = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        exoPlayer.trackSelectionParameters = newParams
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        onSubtitleUpdated("Off")
    }
}

@OptIn(UnstableApi::class)
private fun getQualityOptions(
    context: Context,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    onLabelUpdated: (String) -> Unit
): List<TrackOption> {
    val options = mutableListOf<TrackOption>()
    val tracks = exoPlayer.currentTracks
    val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }

    data class VideoTrackItem(
        val label: String,
        val height: Int,
        val group: Tracks.Group,
        val index: Int,
        val isSelected: Boolean
    )

    val items = mutableListOf<VideoTrackItem>()
    for (group in videoGroups) {
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val height = format.height
            if (height > 0) {
                val label = if (height == 720) "HD 720P" else "${height}p"
                val isSelected = group.isTrackSelected(i)
                items.add(VideoTrackItem(label, height, group, i, isSelected))
            }
        }
    }

    val sortedItems = items.sortedByDescending { it.height }.distinctBy { it.label }

    for (item in sortedItems) {
        options.add(
            TrackOption(
                id = "${item.height}",
                label = item.label,
                isSelected = item.isSelected,
                onSelect = {
                    val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .addOverride(TrackSelectionOverride(item.group.mediaTrackGroup, item.index))
                        .build()
                    exoPlayer.trackSelectionParameters = newParams
                    trackSelector.parameters = trackSelector.buildUponParameters()
                        .setOverrideForType(TrackSelectionOverride(item.group.mediaTrackGroup, item.index))
                        .build()
                    onLabelUpdated(item.label)
                    PlayerPreferences.savePreferredQuality(context, item.label)
                }
            )
        )
    }

    if (options.isEmpty()) {
        listOf("1080p", "HD 720P", "480p", "360p").forEach { res ->
            options.add(
                TrackOption(
                    id = res,
                    label = res,
                    isSelected = res == "1080p",
                    onSelect = {
                        onLabelUpdated(res)
                        PlayerPreferences.savePreferredQuality(context, res)
                    }
                )
            )
        }
    }

    return options
}

@OptIn(UnstableApi::class)
private fun getSubtitleOptions(
    context: Context,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    onLabelUpdated: (String) -> Unit
): List<TrackOption> {
    val options = mutableListOf<TrackOption>()
    val tracks = exoPlayer.currentTracks
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val isDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    options.add(
        TrackOption(
            id = "off",
            label = "Off",
            isSelected = isDisabled,
            onSelect = {
                val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()
                exoPlayer.trackSelectionParameters = newParams
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()
                onLabelUpdated("Off")
                PlayerPreferences.savePreferredSubtitle(context, "Off")
            }
        )
    )

    for ((groupIndex, group) in textGroups.withIndex()) {
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val label = getTrackDisplayLabel(format, "Subtitle", i)
            val isSelected = !isDisabled && group.isTrackSelected(i)
            val trackGroup = group.mediaTrackGroup
            options.add(
                TrackOption(
                    id = "sub_${groupIndex}_$i",
                    label = label,
                    isSelected = isSelected,
                    onSelect = {
                        val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .addOverride(TrackSelectionOverride(trackGroup, i))
                            .build()
                        exoPlayer.trackSelectionParameters = newParams
                        trackSelector.parameters = trackSelector.buildUponParameters()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(trackGroup, i))
                            .build()
                        onLabelUpdated(label)
                        PlayerPreferences.savePreferredSubtitle(context, label)
                    }
                )
            )
        }
    }

    return options
}

@OptIn(UnstableApi::class)
private fun getAudioOptions(
    context: Context,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    onLabelUpdated: (String) -> Unit
): List<TrackOption> {
    val options = mutableListOf<TrackOption>()
    val tracks = exoPlayer.currentTracks
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

    for ((groupIndex, group) in audioGroups.withIndex()) {
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val label = getTrackDisplayLabel(format, "Audio", i)
            val isSelected = group.isTrackSelected(i)
            val trackGroup = group.mediaTrackGroup
            options.add(
                TrackOption(
                    id = "audio_${groupIndex}_$i",
                    label = label,
                    isSelected = isSelected,
                    onSelect = {
                        val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .addOverride(TrackSelectionOverride(trackGroup, i))
                            .build()
                        exoPlayer.trackSelectionParameters = newParams
                        trackSelector.parameters = trackSelector.buildUponParameters()
                            .setOverrideForType(TrackSelectionOverride(trackGroup, i))
                            .build()
                        onLabelUpdated(label)
                        PlayerPreferences.savePreferredAudio(context, label)
                    }
                )
            )
        }
    }

    return options
}

private fun getSpeedOptions(
    exoPlayer: ExoPlayer,
    currentSpeed: String,
    onSpeedSelected: (String) -> Unit
): List<TrackOption> {
    val speeds = listOf(
        0.5f to "0.5x",
        0.75f to "0.75x",
        1.0f to "1.0x",
        1.25f to "1.25x",
        1.5f to "1.5x",
        2.0f to "2.0x"
    )
    return speeds.map { (speedValue, label) ->
        TrackOption(
            id = "speed_$speedValue",
            label = label,
            isSelected = (currentSpeed == label || (currentSpeed == "1.0x" && speedValue == 1.0f)),
            onSelect = {
                exoPlayer.setPlaybackSpeed(speedValue)
                onSpeedSelected(label)
            }
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun calculateSeekStep(holdDurationMs: Long, totalDurationMs: Long): Long {
    val baseStep = when {
        holdDurationMs < 800L -> 10_000L       // 10s (Single tap & initial press)
        holdDurationMs < 2000L -> 30_000L      // 30s (Short hold for intros/recaps)
        holdDurationMs < 4000L -> 60_000L      // 1 minute (Medium hold)
        holdDurationMs < 6500L -> 120_000L     // 2 minutes (Sustained hold)
        else -> 300_000L                       // 5 minutes max base step
    }

    // For movies (> 60 mins), dynamically scale up higher tiers so large 3hr movies can be traversed effortlessly:
    return if (totalDurationMs > 3_600_000L && holdDurationMs >= 2000L) {
        val durationRatioStep = when {
            holdDurationMs < 4000L -> totalDurationMs / 120 // ~1.5 min per tick on a 3hr movie
            holdDurationMs < 6500L -> totalDurationMs / 60  // ~3 min per tick on a 3hr movie
            else -> totalDurationMs / 25                    // ~7.2 min per tick on a 3hr movie
        }
        maxOf(baseStep, durationRatioStep).coerceAtMost(600_000L) // Cap at 10 min per tick
    } else {
        baseStep
    }
}

private fun formatSeekDelta(millis: Long): String {
    val sign = if (millis >= 0) "+" else "-"
    val absSeconds = kotlin.math.abs(millis) / 1000
    val hours = absSeconds / 3600
    val minutes = (absSeconds % 3600) / 60
    val seconds = absSeconds % 60
    return if (hours > 0) {
        String.format("%s%d:%02d:%02d", sign, hours, minutes, seconds)
    } else if (minutes > 0) {
        String.format("%s%d:%02d min", sign, minutes, seconds)
    } else {
        String.format("%s%ds", sign, seconds)
    }
}

