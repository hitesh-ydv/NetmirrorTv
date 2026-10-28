package com.netmirror.netmirrortv.ui.player

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.util.TypedValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Format
import android.util.Log
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import java.io.File
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.netmirror.netmirrortv.model.ServerItem
import com.netmirror.netmirrortv.util.TokenStore
import com.netmirror.netmirrortv.utils.Constants
import com.netmirror.netmirrortv.utils.PlaybackProgressStore
import com.netmirror.netmirrortv.utils.PlayerPreferences
import kotlinx.coroutines.delay
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.zIndex
import java.util.Locale


@OptIn(UnstableApi::class)
private object PlayerCacheManager {
    private const val MAX_CACHE_SIZE: Long = 512 * 1024 * 1024 // 512MB LRU disk cache
    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        val appContext = context.applicationContext
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(appContext.cacheDir, "media3_hls_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
                val databaseProvider = StandaloneDatabaseProvider(appContext)
                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCache = it
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private object CenterBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val itemCenter = offset + size / 2f
        val containerCenter = containerSize / 2f
        return itemCenter - containerCenter
    }
}

enum class MenuTab {
    AUDIO, SUBTITLE, RESOLUTION,SERVER, SPEED, SUBTITLE_SETTINGS
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

val AccentGreen = Color(0xFF46D369)

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
        viewModel.loadStream(id, server = "S1")
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
                val mainTitle = s.response.title?.takeIf { it.isNotBlank() } ?: s.response.titleName.orEmpty()
                val rawEp = s.response.ep.orEmpty()
                val rawEpTitle = s.response.epTitle.orEmpty()
                val referer = s.response.dynamicReferer ?: ""

                if (streamUrl.isNotBlank()) {
                    PlayerContent(
                        streamUrl = streamUrl,
                        title = title,
                        mainTitle = mainTitle,
                        ep = rawEp,
                        epTitle = rawEpTitle,
                        referer = referer,
                        id = id,
                        mainId = mainId,
                        type = type,
                        activeServerCode = s.server,
                        serverList = s.servers,
                        onServerSelected = { serverItem ->
                            viewModel.loadStream(id = id, server = serverItem.headerCode, force = true)
                        },
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

@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
private fun PlayerContent(
    streamUrl: String,
    title: String,
    mainTitle: String = "",
    ep: String = "",
    epTitle: String = "",
    referer: String = "https://net52.cc",
    id: String = "",
    mainId: String = "",
    type: String = "",
    activeServerCode: String = "S1",
    serverList: List<ServerItem> = emptyList(),
    onServerSelected: (ServerItem) -> Unit = {},
    onThirtySecondsPlayed: () -> Unit = {}
) {
    val context = LocalContext.current
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val isMovie = epTitle.equals("MOVIE", ignoreCase = true) || type.equals("m", ignoreCase = true)
    val displayEp = if (!isMovie && ep.isNotBlank()) ep.trim() else ""
    val displayEpTitle = if (!isMovie && epTitle.isNotBlank()) epTitle.trim() else ""
    val displayMainTitle = if (mainTitle.isNotBlank()) {
        mainTitle.trim()
    } else if (title.isNotBlank() && displayEpTitle.isBlank()) {
        title.trim()
    } else {
        ""
    }
    val hasHeaderContent = displayMainTitle.isNotBlank() || displayEp.isNotBlank() || displayEpTitle.isNotBlank()

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
            setUserAgent(Constants.DEFAULT_USER_AGENT)
            setDefaultRequestProperties(
                buildMap {
                    put("Referer", referer)
                    put("User-Agent", Constants.DEFAULT_USER_AGENT)
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
                600_000,  // Minimum buffer: 10 minutes
                600_000,  // Maximum buffer: 10 minutes
                500,      // Initial playback buffer: 500 ms
                500       // Rebuffer playback threshold: 500 ms
            )
            .setBackBuffer(
                120_000, // Back buffer: 2 minutes
                true     // Retain back buffer from keyframes
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setAllowedVideoJoiningTimeMs(5000)
            setMediaCodecSelector(MediaCodecSelector.DEFAULT)
        }

        val hlsMediaSourceFactory = HlsMediaSource.Factory(cacheDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .setMetadataType(HlsMediaSource.METADATA_TYPE_ID3)

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val hlsMediaSource = hlsMediaSourceFactory.createMediaSource(mediaItem)

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setSeekParameters(SeekParameters.EXACT)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                addAnalyticsListener(object : AnalyticsListener {
                    override fun onDroppedVideoFrames(
                        eventTime: AnalyticsListener.EventTime,
                        droppedFrames: Int,
                        elapsedMs: Long
                    ) {
                        Log.d(
                            "PLAYERVIEW",
                            "Dropped=$droppedFrames elapsed=$elapsedMs"
                        )
                    }

                    override fun onVideoDecoderInitialized(
                        eventTime: AnalyticsListener.EventTime,
                        decoderName: String,
                        initializedTimestampMs: Long,
                        initializationDurationMs: Long
                    ) {
                        Log.d("PLAYERVIEW", "Decoder=$decoderName")
                    }
                })
                setMediaSource(hlsMediaSource)
                prepare()
                playWhenReady = true
            }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var playerPlaybackState by remember { mutableStateOf(exoPlayer.playbackState) }
    var playerPlayWhenReady by remember { mutableStateOf(exoPlayer.playWhenReady) }
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

    // Subtitle Custom Style State (Font Size, Text Color, Background Color, Opacity, Bottom Spacing)
    val initialSubSize = remember { PlayerPreferences.getPreferredSubtitleSize(context) }
    val initialSubColorName = remember { PlayerPreferences.getPreferredSubtitleColor(context) ?: "White" }
    val initialSubBgColorName = remember { PlayerPreferences.getPreferredSubtitleBgColor(context) }
    val initialSubBgOpacity = remember { PlayerPreferences.getPreferredSubtitleBgOpacity(context) }
    val initialSubSpacing = remember { PlayerPreferences.getPreferredSubtitleSpacing(context) }

    var selectedSubtitleFontSize by remember { mutableStateOf(initialSubSize) }
    var selectedSubtitleColorName by remember { mutableStateOf(initialSubColorName) }
    var selectedSubtitleBgColorName by remember { mutableStateOf(initialSubBgColorName) }
    var selectedSubtitleBgOpacity by remember { mutableStateOf(initialSubBgOpacity) }
    var selectedSubtitleBottomSpacing by remember { mutableStateOf(initialSubSpacing) }

    // Exit Toast Dialog State
    var showExitToast by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Focus & Navigation State
    var activeFocusArea by remember { mutableStateOf(PlayerFocusArea.PLAY_PAUSE) }
    var activeMenuTab by remember { mutableStateOf(MenuTab.AUDIO) }

    // Focus Requesters
    val playPauseFocusRequester = remember { FocusRequester() }
    val tabAudioFocusRequester = remember { FocusRequester() }
    val tabSubtitleFocusRequester = remember { FocusRequester() }
    val tabResolutionFocusRequester = remember { FocusRequester() }
    val tabSpeedFocusRequester = remember { FocusRequester() }
    val tabSubtitleSettingsFocusRequester = remember { FocusRequester() }
    val tabServerFocusRequester = remember { FocusRequester() }
    val contentFirstOptionFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }


    // Fast seeking state
    var isFastSeeking by remember { mutableStateOf(false) }
    var showSeekOverlay by remember { mutableStateOf(false) }
    var fastSeekTargetPos by remember { mutableLongStateOf(0L) }
    var fastSeekAccumulator by remember { mutableLongStateOf(0L) }
    var seekHoldStartTime by remember { mutableLongStateOf(0L) }
    var lastSeekDirection by remember { mutableStateOf(0) } // -1 for Left, 1 for Right
    var seekEventTrigger by remember { mutableLongStateOf(0L) }
    var lastSeekWasPreloaded by remember { mutableStateOf(false) }

    // Genuinely waiting for media spinner state:
    // When seeking within preloaded buffer or backbuffer, decoding frames takes 200-500ms without needing network chunks.
    // We suppress the spinner completely for preloaded seeks (using a 1500ms safety window) so it never flashes.
    // When seeking into unbuffered network territory, the spinner appears after 450ms.
    val isWaitingForMedia = playerPlaybackState == Player.STATE_BUFFERING && playerPlayWhenReady
    val isInitialPreparing = (duration <= 0L || duration == C.TIME_UNSET) && playerPlaybackState != Player.STATE_ENDED

    LaunchedEffect(isWaitingForMedia, isInitialPreparing, isFastSeeking) {
        if (isFastSeeking) {
            isBuffering = false
            return@LaunchedEffect
        }
        if (isInitialPreparing) {
            delay(200L)
            isBuffering = true
        } else if (isWaitingForMedia) {
            val graceDelay = if (lastSeekWasPreloaded) 1500L else 450L
            delay(graceDelay)
            if (isWaitingForMedia && !isFastSeeking) {
                isBuffering = true
            }
        } else {
            isBuffering = false
            lastSeekWasPreloaded = false
        }
    }

    // Auto-dismiss seek pop-up card 1.5 seconds after key release
    LaunchedEffect(isFastSeeking, seekEventTrigger) {
        if (isFastSeeking) {
            showSeekOverlay = true
        } else if (showSeekOverlay) {
            delay(1500L)
            showSeekOverlay = false
            fastSeekAccumulator = 0L
            lastSeekDirection = 0
        }
    }

    // Debounce commit for rapid repeated seeks & IR remotes without KeyUp
    LaunchedEffect(fastSeekTargetPos, isFastSeeking) {
        if (isFastSeeking) {
            delay(120L)
            if (isFastSeeking) {
                val isInsidePreload = (fastSeekTargetPos >= currentPosition && fastSeekTargetPos <= bufferedPosition) ||
                                      (fastSeekTargetPos < currentPosition && (currentPosition - fastSeekTargetPos) <= 120_000L)
                lastSeekWasPreloaded = isInsidePreload
                exoPlayer.seekTo(fastSeekTargetPos)
                currentPosition = fastSeekTargetPos
                isFastSeeking = false
                seekHoldStartTime = 0L
                try {
                    playPauseFocusRequester.requestFocus()
                } catch (_: Exception) {
                }
            }
        }
    }

    // Realtime Subtitle Style Update Effect
    LaunchedEffect(
        playerViewRef,
        selectedSubtitleFontSize,
        selectedSubtitleColorName,
        selectedSubtitleBgColorName,
        selectedSubtitleBgOpacity,
        selectedSubtitleBottomSpacing
    ) {
        val colorInt = getSubtitleTextColor(selectedSubtitleColorName)
        val bgColorInt = getSubtitleBgColor(selectedSubtitleBgColorName, selectedSubtitleBgOpacity)
        val density = context.resources.displayMetrics.density
        val bottomMarginPx = (density * selectedSubtitleBottomSpacing).toInt()

        playerViewRef?.subtitleView?.apply {
            setStyle(
                CaptionStyleCompat(
                    colorInt,
                    bgColorInt,
                    android.graphics.Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    null
                )
            )
            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, selectedSubtitleFontSize)
            setPadding(0, 0, 0, bottomMarginPx)
            setBottomPaddingFraction(selectedSubtitleBottomSpacing / 600f)
        }
    }

    var currentActiveServer by remember(activeServerCode) { mutableStateOf(activeServerCode) }
    val displayServers = remember(serverList, currentActiveServer) {
        serverList.map { s ->
            s.copy(active = s.headerCode.equals(currentActiveServer, ignoreCase = true))
        }
    }


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
        val exactDuration = if (exoPlayer.duration > 0L && exoPlayer.duration != C.TIME_UNSET) exoPlayer.duration else duration
        if (resumeAtMs < (exactDuration - 5000L).coerceAtLeast(0L)) {
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
            if (!isFastSeeking) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            val exactDuration = exoPlayer.duration
            if (exactDuration > 0L && exactDuration != C.TIME_UNSET) {
                duration = exactDuration
                if (!metaLoaded) {
                    metaLoaded = true
                }
            }
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            playerPlaybackState = exoPlayer.playbackState
            playerPlayWhenReady = exoPlayer.playWhenReady

            // Fire 30-second trigger once current playback position hits 30 seconds (30,000ms)
            if (!thirtySecondTriggered && currentPosition >= 30_000L) {
                thirtySecondTriggered = true
            }

            delay(250L)
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

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                val exactDuration = exoPlayer.duration
                if (exactDuration > 0L && exactDuration != C.TIME_UNSET) {
                    duration = exactDuration
                    metaLoaded = true
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playerPlaybackState = playbackState
                val exactDuration = exoPlayer.duration
                if (exactDuration > 0L && exactDuration != C.TIME_UNSET) {
                    duration = exactDuration
                    metaLoaded = true
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playerPlayWhenReady = playWhenReady
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
                val exactDuration = exoPlayer.duration
                if (exactDuration > 0L && exactDuration != C.TIME_UNSET) {
                    duration = exactDuration
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

    // Pause player when app gets into background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
                if (thirtySecondTriggered && exoPlayer.currentPosition > 0L && exoPlayer.duration > 0L) {
                    val curSec = exoPlayer.currentPosition / 1000L
                    val durSec = exoPlayer.duration / 1000L
                    PlaybackProgressStore.saveProgress(context, id, curSec, durSec)
                    Log.d("PlayerProgress", "Saved progress on background pause: ${curSec}s (progress_$id = $curSec:$durSec)")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                                        MenuTab.SUBTITLE_SETTINGS -> tabSubtitleSettingsFocusRequester
                                        MenuTab.SERVER -> tabServerFocusRequester
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
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (isFastSeeking) {
                                val isInsidePreload = (fastSeekTargetPos >= currentPosition && fastSeekTargetPos <= bufferedPosition) ||
                                                      (fastSeekTargetPos < currentPosition && (currentPosition - fastSeekTargetPos) <= 120_000L)
                                lastSeekWasPreloaded = isInsidePreload
                                exoPlayer.seekTo(fastSeekTargetPos)
                                currentPosition = fastSeekTargetPos
                                isFastSeeking = false
                                seekHoldStartTime = 0L
                                seekEventTrigger = System.currentTimeMillis()
                                try {
                                    playPauseFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionLeft, Key.MediaRewind, Key.MediaStepBackward -> {
                            if (!controlsVisible || !isMenuVisible || activeFocusArea == PlayerFocusArea.PLAY_PAUSE) {
                                controlsVisible = true
                                activeFocusArea = PlayerFocusArea.PLAY_PAUSE
                                try {
                                    playPauseFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                }
                                val playerDuration = exoPlayer.duration
                                val maxDur = if (playerDuration > 0L) playerDuration else Long.MAX_VALUE
                                val now = System.currentTimeMillis()

                                // Chain position from previous tap if overlay is still active in same direction:
                                val isChainingPosition = (isFastSeeking || showSeekOverlay) && lastSeekDirection == -1
                                val startPos = if (isChainingPosition) fastSeekTargetPos else exoPlayer.currentPosition

                                // Only continue hold acceleration timer if physically holding (isFastSeeking == true)
                                if (!isFastSeeking || lastSeekDirection != -1) {
                                    seekHoldStartTime = now
                                    lastSeekDirection = -1
                                }
                                if (!isChainingPosition) {
                                    fastSeekAccumulator = 0L
                                }

                                val holdDuration = now - seekHoldStartTime
                                val step = calculateSeekStep(holdDuration, if (playerDuration > 0L) playerDuration else 0L)

                                val newTarget = (startPos - step).coerceAtLeast(0L)
                                isFastSeeking = true
                                showSeekOverlay = true
                                seekEventTrigger = now
                                fastSeekTargetPos = newTarget
                                fastSeekAccumulator -= step
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionRight, Key.MediaFastForward, Key.MediaStepForward -> {
                            if (!controlsVisible || !isMenuVisible || activeFocusArea == PlayerFocusArea.PLAY_PAUSE) {
                                controlsVisible = true
                                activeFocusArea = PlayerFocusArea.PLAY_PAUSE
                                try {
                                    playPauseFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                }
                                val playerDuration = exoPlayer.duration
                                val maxDur = if (playerDuration > 0L) playerDuration else Long.MAX_VALUE
                                val now = System.currentTimeMillis()

                                // Chain position from previous tap if overlay is still active in same direction:
                                val isChainingPosition = (isFastSeeking || showSeekOverlay) && lastSeekDirection == 1
                                val startPos = if (isChainingPosition) fastSeekTargetPos else exoPlayer.currentPosition

                                // Only continue hold acceleration timer if physically holding (isFastSeeking == true)
                                if (!isFastSeeking || lastSeekDirection != 1) {
                                    seekHoldStartTime = now
                                    lastSeekDirection = 1
                                }
                                if (!isChainingPosition) {
                                    fastSeekAccumulator = 0L
                                }

                                val holdDuration = now - seekHoldStartTime
                                val step = calculateSeekStep(holdDuration, if (playerDuration > 0L) playerDuration else 0L)

                                val newTarget = (startPos + step).coerceAtMost(maxDur)
                                isFastSeeking = true
                                showSeekOverlay = true
                                seekEventTrigger = now
                                fastSeekTargetPos = newTarget
                                fastSeekAccumulator += step
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                    if ((event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                         event.key == Key.MediaRewind || event.key == Key.MediaFastForward ||
                         event.key == Key.MediaStepBackward || event.key == Key.MediaStepForward) && isFastSeeking) {
                        val now = System.currentTimeMillis()
                        val holdDuration = now - seekHoldStartTime
                        if (holdDuration >= 400L) {
                            // Sustained hold released: commit seek immediately
                            val isInsidePreload = (fastSeekTargetPos >= currentPosition && fastSeekTargetPos <= bufferedPosition) ||
                                                  (fastSeekTargetPos < currentPosition && (currentPosition - fastSeekTargetPos) <= 120_000L)
                            lastSeekWasPreloaded = isInsidePreload
                            exoPlayer.seekTo(fastSeekTargetPos)
                            currentPosition = fastSeekTargetPos
                            isFastSeeking = false
                            seekHoldStartTime = 0L
                            seekEventTrigger = now
                            try {
                                playPauseFocusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                            return@onPreviewKeyEvent true
                        } else {
                            // Quick tap: let LaunchedEffect(fastSeekTargetPos, isFastSeeking) commit after 120ms
                            seekEventTrigger = now
                            return@onPreviewKeyEvent true
                        }
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
                        val colorInt = getSubtitleTextColor(selectedSubtitleColorName)
                        val bgColorInt = getSubtitleBgColor(selectedSubtitleBgColorName, selectedSubtitleBgOpacity)
                        val density = ctx.resources.displayMetrics.density
                        val bottomMarginPx = (density * selectedSubtitleBottomSpacing).toInt()

                        setStyle(
                            CaptionStyleCompat(
                                colorInt,
                                bgColorInt,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null
                            )
                        )
                        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, selectedSubtitleFontSize)
                        setPadding(0, 0, 0, bottomMarginPx)
                        setBottomPaddingFraction(selectedSubtitleBottomSpacing / 600f)
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
                .zIndex(5f)
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
            visible = controlsVisible && hasHeaderContent,
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
                    .padding(top = 22.dp, bottom = 44.dp, start = 40.dp, end = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {

                    if (displayEp.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                                .padding(horizontal = 8.dp, vertical = 2.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayEp,
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }


                    if (displayMainTitle.isNotBlank() && (displayEp.isNotBlank() || displayEpTitle.isNotBlank())) {
                        Text(
                            text = "|",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    if (displayMainTitle.isNotBlank()) {
                        Text(
                            text = displayMainTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    if (displayMainTitle.isNotBlank() && displayEpTitle.isNotBlank()) {
                        Text(
                            text = "|",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    if (displayEpTitle.isNotBlank()) {
                        Text(
                            text = displayEpTitle,
                            color = AccentGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Buffering Indicator (Clean Circular Spinner without text)
        if (isBuffering && !isFastSeeking) {
            CircularProgressIndicator(
                color = Color(0xFFE50914),
                trackColor = Color.White.copy(alpha = 0.2f),
                strokeWidth = 3.5.dp,
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
            )
        }

        // Left Side: Rewind Pop-up Seek Card (Flush to left screen edge)
        AnimatedVisibility(
            visible = showSeekOverlay && (lastSeekDirection == -1 || fastSeekAccumulator < 0),
            enter = fadeIn(tween(150)) + slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(150)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(150)) + slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(150)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            val leftCardShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 0.dp, bottomStart = 0.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(105.dp)
                    .shadow(elevation = 10.dp, shape = leftCardShape)
                    .clip(leftCardShape)
                    .background(Color(0xFF141414).copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), leftCardShape)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatSeekDelta(fastSeekAccumulator),
                        color = Color(0xFFE50914),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
//                    Spacer(Modifier.height(2.dp))
//                    Text(
//                        text = formatTime(fastSeekTargetPos),
//                        color = Color.White,
//                        fontSize = 12.sp,
//                        fontWeight = FontWeight.SemiBold,
//                        textAlign = TextAlign.Center,
//                        maxLines = 1,
//                        modifier = Modifier.fillMaxWidth()
//                    )
                }
            }
        }

        // Right Side: Forward Pop-up Seek Card (Flush to right screen edge)
        AnimatedVisibility(
            visible = showSeekOverlay && (lastSeekDirection == 1 || fastSeekAccumulator > 0),
            enter = fadeIn(tween(150)) + slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(150)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(150)) + slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(150)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val rightCardShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(105.dp)
                    .shadow(elevation = 10.dp, shape = rightCardShape)
                    .clip(rightCardShape)
                    .background(Color(0xFF141414).copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), rightCardShape)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Fast Forward",
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatSeekDelta(fastSeekAccumulator),
                        color = Color(0xFFE50914),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
//                    Spacer(Modifier.height(2.dp))
//                    Text(
//                        text = formatTime(fastSeekTargetPos),
//                        color = Color.White,
//                        fontSize = 12.sp,
//                        fontWeight = FontWeight.SemiBold,
//                        textAlign = TextAlign.Center,
//                        maxLines = 1,
//                        modifier = Modifier.fillMaxWidth()
//                    )
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
                    val displayPosition = if (isFastSeeking) fastSeekTargetPos else currentPosition
                    val progress = if (duration > 0) (displayPosition.toFloat() / duration.toFloat()) else 0f
                    val bufferProgress = if (duration > 0) (bufferedPosition.toFloat() / duration.toFloat()) else 0f

                    // Compact Play/Pause Button (Left of Left Duration)
                    PlayPauseButton(
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

                    Spacer(Modifier.width(20.dp))

                    // Left Side: Current / Seek Position Time
                    Box(
                        modifier = Modifier
                            .widthIn(min = if (duration >= 3600_000L) 84.dp else 68.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatTime(displayPosition),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Middle: Ultra-Smooth Custom Seekbar with Preload Buffer Visualization
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .height(22.dp)
                    ) {
                        val barHeight = 5.5.dp.toPx()
                        val cornerRadius = CornerRadius(barHeight / 2, barHeight / 2)
                        val centerY = size.height / 2
                        val barTop = centerY - (barHeight / 2)

                        // 1. Background Total Track
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.20f),
                            topLeft = Offset(0f, barTop),
                            size = Size(size.width, barHeight),
                            cornerRadius = cornerRadius
                        )

                        // 2. Base Buffer Track (Entire contiguous buffered range)
                        val bufferEndX = (size.width * bufferProgress.coerceIn(0f, 1f)).coerceAtLeast(0f)
                        if (bufferEndX > 0f) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.35f),
                                topLeft = Offset(0f, barTop),
                                size = Size(bufferEndX, barHeight),
                                cornerRadius = cornerRadius
                            )
                        }

                        // 3. Highlighted Preload Track (Ahead of playhead from current point)
                        val playedWidth = (size.width * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)
                        if (bufferEndX > playedWidth) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.65f),
                                topLeft = Offset(playedWidth, barTop),
                                size = Size(bufferEndX - playedWidth, barHeight),
                                cornerRadius = cornerRadius
                            )
                        }

                        // 4. Played Progress Track with Gradient
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

                        // 5. Scrubber Thumb (Expands slightly when seeking/rewinding)
                        if (progress > 0f || isFastSeeking || showSeekOverlay) {
                            val thumbRadius = if (isFastSeeking || showSeekOverlay) 7.5.dp.toPx() else 6.0.dp.toPx()
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
                                radius = (thumbRadius - 2.2.dp.toPx()).coerceAtLeast(1f),
                                center = Offset(thumbX, centerY)
                            )
                        }
                    }

                    // Right Side: Total Duration Time
                    Box(
                        modifier = Modifier
                            .widthIn(min = if (duration >= 3600_000L) 84.dp else 68.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatTime(duration),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
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
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(
                        start = 40.dp,
                        end = 40.dp,
                        top = 24.dp,
                        bottom = 24.dp
                    )
            ) {
                // Row 1: Separate Menu Tabs (Audio, Subtitle, Resolution, Playback Speed, Subtitle Style, Server)
                CompositionLocalProvider(LocalBringIntoViewSpec provides CenterBringIntoViewSpec) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    ) {
                        item {
                            MenuTabPill(
                                title = "Audio",
                                badge = selectedAudioLabel,
                                isSelected = activeMenuTab == MenuTab.AUDIO,
                                focusRequester = tabAudioFocusRequester,
                                isFirst = true,
                                onFocusGained = {
                                    activeFocusArea = PlayerFocusArea.MENU_TABS
                                    activeMenuTab = MenuTab.AUDIO
                                },
                                onClick = {
                                    activeMenuTab = MenuTab.AUDIO
                                }
                            )
                        }

                        item {
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
                        }

                        item {
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
                        }

                        if (displayServers.size > 1) {
                            item {
                                MenuTabPill(
                                    title = "Server",
                                    badge = displayServers.firstOrNull { it.active }?.name
                                        ?: displayServers.firstOrNull { it.headerCode.equals(currentActiveServer, ignoreCase = true) }?.name
                                        ?: "",
                                    isSelected = activeMenuTab == MenuTab.SERVER,
                                    focusRequester = tabServerFocusRequester,
                                    onFocusGained = {
                                        activeFocusArea = PlayerFocusArea.MENU_TABS
                                        activeMenuTab = MenuTab.SERVER
                                    },
                                    onClick = {
                                        activeMenuTab = MenuTab.SERVER
                                    }
                                )
                            }
                        }

                        item {
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

                        item {
                            MenuTabPill(
                                title = "Subtitle Style",
                                badge = selectedSubtitleColorName,
                                isSelected = activeMenuTab == MenuTab.SUBTITLE_SETTINGS,
                                focusRequester = tabSubtitleSettingsFocusRequester,
                                isLast = true,
                                onFocusGained = {
                                    activeFocusArea = PlayerFocusArea.MENU_TABS
                                    activeMenuTab = MenuTab.SUBTITLE_SETTINGS
                                },
                                onClick = {
                                    activeMenuTab = MenuTab.SUBTITLE_SETTINGS
                                }
                            )
                        }
                    }
                }

                // LazyRow in Menu Content for all tabs
                CompositionLocalProvider(LocalBringIntoViewSpec provides CenterBringIntoViewSpec) {
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
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 10.dp,
                                            vertical = 6.dp
                                        ),
                                    ) {
                                        itemsIndexed(audioOptions) { index, option ->
                                            OptionContentPill(
                                                label = option.label,
                                                isSelected = option.isSelected,
                                                focusRequester = if (index == 0) contentFirstOptionFocusRequester else null,
                                                isFirst = index == 0,
                                                isLast = index == audioOptions.size - 1,
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
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 10.dp,
                                            vertical = 6.dp
                                        ),
                                    ) {
                                        itemsIndexed(subtitleOptions) { index, option ->
                                            OptionContentPill(
                                                label = option.label,
                                                isSelected = option.isSelected,
                                                focusRequester = if (index == 0) contentFirstOptionFocusRequester else null,
                                                isFirst = index == 0,
                                                isLast = index == subtitleOptions.size - 1,
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
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 10.dp,
                                            vertical = 6.dp
                                        ),
                                    ) {
                                        itemsIndexed(qualityOptions) { index, option ->
                                            OptionContentPill(
                                                label = option.label,
                                                isSelected = option.isSelected,
                                                focusRequester = if (index == 0) contentFirstOptionFocusRequester else null,
                                                isFirst = index == 0,
                                                isLast = index == qualityOptions.size - 1,
                                                onFocusGained = { activeFocusArea = PlayerFocusArea.MENU_CONTENT },
                                                onClick = { option.onSelect() }
                                            )
                                        }
                                    }
                                }
                            }

                            MenuTab.SERVER -> {
                                if (displayServers.size > 1) {
                                    Column {
                                        Text(
                                            text = "Server",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(
                                                horizontal = 10.dp,
                                                vertical = 6.dp
                                            ),
                                        ) {
                                            itemsIndexed(displayServers) { index, serverItem ->
                                                OptionContentPill(
                                                    label = serverItem.name,
                                                    isSelected = serverItem.active,
                                                    focusRequester = if (index == 0) contentFirstOptionFocusRequester else null,
                                                    isFirst = index == 0,
                                                    isLast = index == displayServers.size - 1,
                                                    onFocusGained = { activeFocusArea = PlayerFocusArea.MENU_CONTENT },
                                                    onClick = {
                                                        if (currentActiveServer != serverItem.headerCode) {
                                                            if (exoPlayer.currentPosition > 0L && exoPlayer.duration > 0L) {
                                                                PlaybackProgressStore.saveProgress(
                                                                    context,
                                                                    id,
                                                                    exoPlayer.currentPosition / 1000L,
                                                                    exoPlayer.duration / 1000L
                                                                )
                                                            }
                                                            currentActiveServer = serverItem.headerCode
                                                            onServerSelected(serverItem)
                                                        }
                                                    }
                                                )
                                            }
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
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 10.dp,
                                            vertical = 6.dp
                                        ),
                                    ) {
                                        itemsIndexed(speedOptions) { index, option ->
                                            OptionContentPill(
                                                label = option.label,
                                                isSelected = option.isSelected,
                                                focusRequester = if (index == 0) contentFirstOptionFocusRequester else null,
                                                isFirst = index == 0,
                                                isLast = index == speedOptions.size - 1,
                                                onFocusGained = { activeFocusArea = PlayerFocusArea.MENU_CONTENT },
                                                onClick = { option.onSelect() }
                                            )
                                        }
                                    }
                                }
                            }

                            MenuTab.SUBTITLE_SETTINGS -> {
                                SubtitleSettingsContent(
                                    selectedColorName = selectedSubtitleColorName,
                                    selectedBgColorName = selectedSubtitleBgColorName,
                                    selectedBgOpacity = selectedSubtitleBgOpacity,
                                    selectedSizeSp = selectedSubtitleFontSize,
                                    selectedSpacing = selectedSubtitleBottomSpacing,
                                    firstOptionFocusRequester = contentFirstOptionFocusRequester,
                                    onFocusGained = { activeFocusArea = PlayerFocusArea.MENU_CONTENT },
                                    onColorChanged = { name ->
                                        selectedSubtitleColorName = name
                                        PlayerPreferences.savePreferredSubtitleColor(context, name)
                                    },
                                    onBgColorChanged = { name ->
                                        selectedSubtitleBgColorName = name
                                        PlayerPreferences.savePreferredSubtitleBgColor(context, name)
                                    },
                                    onBgOpacityChanged = { opacity ->
                                        selectedSubtitleBgOpacity = opacity
                                        PlayerPreferences.savePreferredSubtitleBgOpacity(context, opacity)
                                    },
                                    onSizeChanged = { size ->
                                        selectedSubtitleFontSize = size
                                        PlayerPreferences.savePreferredSubtitleSize(context, size)
                                    },
                                    onSpacingChanged = { spacing ->
                                        selectedSubtitleBottomSpacing = spacing
                                        PlayerPreferences.savePreferredSubtitleSpacing(context, spacing)
                                    }
                                )
                            }


                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    focusRequester: FocusRequester,
    onFocusGained: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
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
            .size(36.dp)
            .clip(CircleShape)
            .background(
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                shape = CircleShape
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.25f),
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
                .size(20.dp)
                .then(
                    if (!isPlaying) Modifier.padding(start = 2.dp) else Modifier
                )
        )
    }
}

// When focus leaves tab buttons (Audio, Subtitle, Resolution, Speed), set background to transparent and text color to white
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuTabPill(
    title: String,
    badge: String?,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onFocusGained: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    val bgColor = if (isFocused) Color.White else Color.Transparent
    val textColor = if (isFocused) Color.Black else Color.White
    val badgeColor = if (isFocused) Color.DarkGray else Color.White.copy(alpha = 0.8f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
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
                if (event.type == KeyEventType.KeyDown) {
                    if (isFirst && event.key == Key.DirectionLeft) {
                        return@onPreviewKeyEvent true
                    }
                    if (isLast && event.key == Key.DirectionRight) {
                        return@onPreviewKeyEvent true
                    }
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        onClick()
                        return@onPreviewKeyEvent true
                    }
                }
                false
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OptionContentPill(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onFocusGained: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val requester = focusRequester ?: remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(requester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocusGained()
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFFE50914).copy(alpha = 0.3f)
                    else -> Color.White.copy(alpha = 0.1f)
                }
            )
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFFE50914)
                    else -> Color.White.copy(alpha = 0.2f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (isFirst && event.key == Key.DirectionLeft) {
                        return@onPreviewKeyEvent true
                    }
                    if (isLast && event.key == Key.DirectionRight) {
                        return@onPreviewKeyEvent true
                    }
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        onClick()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.Black else if (isSelected) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
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

    // 1. Initial Quality Selection Logic (Saved Pref -> 1080p -> 720p -> 480p -> Highest)
    val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    val savedQuality = PlayerPreferences.getPreferredQuality(context)

    data class QualityCandidate(val bitrate: Int, val label: String, val group: Tracks.Group, val index: Int)
    val qualityCandidates = mutableListOf<QualityCandidate>()

    for (group in videoGroups) {
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val label = getVideoTrackLabel(format)
            if (label != "Video") {
                val bitrate = if (format.bitrate > 0) format.bitrate else if (format.peakBitrate > 0) format.peakBitrate else 0
                qualityCandidates.add(QualityCandidate(bitrate, label, group, i))
            }
        }
    }

    if (qualityCandidates.isNotEmpty()) {
        val targetQuality = qualityCandidates.firstOrNull { it.label.equals(savedQuality, ignoreCase = true) }
            ?: qualityCandidates.firstOrNull { it.label.equals("1080p", ignoreCase = true) || it.bitrate == 1000000 }
            ?: qualityCandidates.firstOrNull { it.label.equals("720p", ignoreCase = true) || it.bitrate == 600000 }
            ?: qualityCandidates.firstOrNull { it.label.equals("480p", ignoreCase = true) || it.bitrate == 400000 }
            ?: qualityCandidates.maxByOrNull { it.bitrate }
            ?: qualityCandidates.firstOrNull()

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

private fun getVideoTrackLabel(format: Format): String {
    return if (format.width > 0 || format.height > 0) {
        val bitrate = if (format.bitrate != Format.NO_VALUE && format.bitrate > 0) {
            format.bitrate
        } else if (format.peakBitrate != Format.NO_VALUE && format.peakBitrate > 0) {
            format.peakBitrate
        } else {
            0
        }

        when (bitrate) {
            1000000 -> "1080p"
            600000 -> "720p"
            400000 -> "480p"
            else -> {
                if (bitrate > 0) {
                    "${bitrate}p"
                } else if (format.height > 0) {
                    "${format.height}p"
                } else {
                    "Video"
                }
            }
        }
    } else {
        "Video"
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
        val bitrate: Int,
        val group: Tracks.Group,
        val index: Int,
        val isSelected: Boolean
    )

    val items = mutableListOf<VideoTrackItem>()
    for (group in videoGroups) {
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val label = getVideoTrackLabel(format)
            if (label != "Video") {
                val bitrate = if (format.bitrate > 0) format.bitrate else if (format.peakBitrate > 0) format.peakBitrate else 0
                val isSelected = group.isTrackSelected(i)
                items.add(VideoTrackItem(label, bitrate, group, i, isSelected))
            }
        }
    }

    val sortedItems = items.sortedByDescending { it.bitrate }.distinctBy { it.label }

    for (item in sortedItems) {
        options.add(
            TrackOption(
                id = "${item.bitrate}_${item.label}",
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
        listOf("1080p", "720p", "480p").forEach { res ->
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
    val totalSeconds = millis.coerceAtLeast(0L) / 1000
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
    return when {
        // 1. Taps / Short Hold (< 0.8s): Stays at 10s (fine-grained navigation for dialogues and small skips)
        holdDurationMs < 800L -> 10_000L

        // 2. Short Hold (0.8s – 2.0s): Ramps up to 30s per tick (quickly skips intros & recaps)
        holdDurationMs < 2000L -> 30_000L

        // 3. Medium Hold (2.0s – 4.0s): Jumps 1 minute (or scaled proportionally to content length)
        holdDurationMs < 4000L -> {
            val baseStep = 60_000L // 1 minute
            if (totalDurationMs > 3_600_000L) {
                maxOf(baseStep, totalDurationMs / 120) // ~1.5 min per tick for a 3-hour movie
            } else {
                baseStep
            }
        }

        // 4. Sustained Hold (4.0s+): Dynamically scales to ~5%–8% of total duration per second
        // (~3–7 minutes per tick for a 3-hour movie, crossing 3-hour movie in ~5–6s)
        else -> {
            if (totalDurationMs > 0L) {
                val stepRatio = if (holdDurationMs < 5500L) 0.05f else 0.08f
                // Scale ~3 to ~7 min per tick for 3hr movie (180min * 0.05 / 2.5 ~ 3.6min, 180min * 0.08 / 2.0 ~ 7.2min)
                val dynamicStep = if (holdDurationMs < 5500L) {
                    maxOf(180_000L, (totalDurationMs * stepRatio / 2.5f).toLong()) // ~3–4 min
                } else {
                    maxOf(300_000L, (totalDurationMs * stepRatio / 2.0f).toLong()) // ~6–7 min
                }
                dynamicStep.coerceAtMost(600_000L) // Capped at 10 min max per tick
            } else {
                if (holdDurationMs < 5500L) 180_000L else 360_000L
            }
        }
    }
}

private fun formatSeekDelta(millis: Long): String {
    val sign = if (millis >= 0) "+" else "-"
    val absSeconds = kotlin.math.abs(millis) / 1000
    val hours = absSeconds / 3600
    val minutes = (absSeconds % 3600) / 60
    val seconds = absSeconds % 60
    return when {
        hours > 0 && minutes > 0 -> "$sign${hours}h ${minutes}m"
        hours > 0 -> "$sign${hours}h"
        minutes > 0 && seconds > 0 -> "$sign${minutes}m ${seconds}s"
        minutes > 0 -> "$sign${minutes}m"
        else -> "$sign${seconds}s"
    }
}

private fun getSubtitleTextColor(colorName: String): Int {
    return when (colorName.lowercase()) {
        "yellow" -> android.graphics.Color.YELLOW
        "green" -> android.graphics.Color.parseColor("#00E676")
        "purple" -> android.graphics.Color.parseColor("#BA68C8")
        "cyan" -> android.graphics.Color.CYAN
        else -> android.graphics.Color.WHITE
    }
}

private fun getSubtitleBgColor(colorName: String, opacity: Float): Int {
    val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
    if (alpha <= 0) return android.graphics.Color.TRANSPARENT
    val baseColor = when (colorName.lowercase()) {
        "white" -> android.graphics.Color.WHITE
        "dark gray" -> android.graphics.Color.parseColor("#2B2B2B")
        "navy blue", "navy", "dark blue" -> android.graphics.Color.parseColor("#0D1B2A")
        else -> android.graphics.Color.BLACK
    }
    return android.graphics.Color.argb(
        alpha,
        android.graphics.Color.red(baseColor),
        android.graphics.Color.green(baseColor),
        android.graphics.Color.blue(baseColor)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtitleSettingsContent(
    selectedColorName: String,
    selectedBgColorName: String,
    selectedBgOpacity: Float,
    selectedSizeSp: Float,
    selectedSpacing: Float,
    firstOptionFocusRequester: FocusRequester,
    onFocusGained: () -> Unit,
    onColorChanged: (String) -> Unit,
    onBgColorChanged: (String) -> Unit,
    onBgOpacityChanged: (Float) -> Unit,
    onSizeChanged: (Float) -> Unit,
    onSpacingChanged: (Float) -> Unit
) {
    data class SubtitleColorItem(val name: String, val color: Color)
    val colorItems = remember {
        listOf(
            SubtitleColorItem("White", Color(0xFFFFFFFF)),
            SubtitleColorItem("Yellow", Color(0xFFFFEB3B)),
            SubtitleColorItem("Green", Color(0xFF00E676)),
            SubtitleColorItem("Purple", Color(0xFFBA68C8)),
            SubtitleColorItem("Cyan", Color(0xFF00E5FF))
        )
    }

    val bgColorItems = remember {
        listOf(
            SubtitleColorItem("Black", Color(0xFF000000)),
            SubtitleColorItem("White", Color(0xFFFFFFFF)),
            SubtitleColorItem("Dark Gray", Color(0xFF2B2B2B)),
            SubtitleColorItem("Navy Blue", Color(0xFF0D1B2A))
        )
    }

    data class SubtitleOpacityItem(val label: String, val opacity: Float)
    val opacityItems = remember {
        listOf(
            SubtitleOpacityItem("0", 0.0f),
            SubtitleOpacityItem("0.25", 0.25f),
            SubtitleOpacityItem("0.5", 0.5f),
            SubtitleOpacityItem("0.75", 0.75f),
            SubtitleOpacityItem("1", 1.0f)
        )
    }

    data class SubtitleSizeItem(val label: String, val size: Float)
    val sizeItems = remember {
        listOf(
            SubtitleSizeItem("18px", 18f),
            SubtitleSizeItem("22px", 22f),
            SubtitleSizeItem("26px", 26f),
            SubtitleSizeItem("30px", 30f),
            SubtitleSizeItem("34px", 34f)
        )
    }

    data class SubtitleSpacingItem(val label: String, val spacing: Float)
    val spacingItems = remember {
        listOf(
            SubtitleSpacingItem("0", 0f),
            SubtitleSpacingItem("20", 20f),
            SubtitleSpacingItem("40", 40f),
            SubtitleSpacingItem("60", 60f),
            SubtitleSpacingItem("80", 80f),
            SubtitleSpacingItem("100", 100f)
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Subtitle Style & Appearance",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        CompositionLocalProvider(LocalBringIntoViewSpec provides CenterBringIntoViewSpec) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
//                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Section 1: Color (Text Color)
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Color:",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        colorItems.forEachIndexed { index, item ->
                            ColorOptionPill(
                                name = item.name,
                                color = item.color,
                                isSelected = selectedColorName.equals(item.name, ignoreCase = true),
                                focusRequester = if (index == 0) firstOptionFocusRequester else null,
                                isFirst = index == 0,
                                onFocusGained = onFocusGained,
                                onSelect = { onColorChanged(item.name) }
                            )
                        }
                    }
                }

                // Section 2: Background Color
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Bg Color:",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        bgColorItems.forEach { item ->
                            ColorOptionPill(
                                name = item.name,
                                color = item.color,
                                isSelected = selectedBgColorName.equals(item.name, ignoreCase = true),
                                onFocusGained = onFocusGained,
                                onSelect = { onBgColorChanged(item.name) }
                            )
                        }
                    }
                }

                // Section 3: Background Opacity
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Opacity:",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        opacityItems.forEach { item ->
                            SubtitleSmallOptionPill(
                                label = item.label,
                                isSelected = kotlin.math.abs(selectedBgOpacity - item.opacity) < 0.05f,
                                onFocusGained = onFocusGained,
                                onClick = { onBgOpacityChanged(item.opacity) }
                            )
                        }
                    }
                }

                // Section 4: Font Size
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Size:",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        sizeItems.forEach { item ->
                            SubtitleSmallOptionPill(
                                label = item.label,
                                isSelected = kotlin.math.abs(selectedSizeSp - item.size) < 0.5f,
                                onFocusGained = onFocusGained,
                                onClick = { onSizeChanged(item.size) }
                            )
                        }
                    }
                }

                // Section 5: Bottom Margin
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Bottom Margin:",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        spacingItems.forEachIndexed { index, item ->
                            SubtitleSmallOptionPill(
                                label = item.label,
                                isSelected = kotlin.math.abs(selectedSpacing - item.spacing) < 0.5f,
                                isLast = index == spacingItems.size - 1,
                                onFocusGained = onFocusGained,
                                onClick = { onSpacingChanged(item.spacing) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorOptionPill(
    name: String,
    color: Color,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onFocusGained: () -> Unit,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocusGained()
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFFE50914).copy(alpha = 0.3f)
                    else -> Color.White.copy(alpha = 0.1f)
                }
            )
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFFE50914)
                    else -> Color.White.copy(alpha = 0.2f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onSelect() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (isFirst && event.key == Key.DirectionLeft) {
                        return@onPreviewKeyEvent true
                    }
                    if (isLast && event.key == Key.DirectionRight) {
                        return@onPreviewKeyEvent true
                    }
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        onSelect()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, if (isFocused) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.6f), CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            color = if (isFocused) Color.Black else if (isSelected) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SubtitleSmallOptionPill(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onFocusGained: () -> Unit,
    onClick: () -> Unit
) {
    OptionContentPill(
        label = label,
        isSelected = isSelected,
        focusRequester = focusRequester,
        isFirst = isFirst,
        isLast = isLast,
        onFocusGained = onFocusGained,
        onClick = onClick
    )
}

