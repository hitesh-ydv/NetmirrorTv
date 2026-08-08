package com.netmirror.netmirrortv.ui.player

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.netmirror.netmirrortv.R

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen() {

    val context = LocalContext.current

    // Track selector is created first so we can:
    // 1. Disable tunneling (prevents the "frame locked in top-left quadrant" bug on some decoders)
    // 2. Cap max video size to 720p as the default ceiling (adaptive below that, never goes above)
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(false)
                    .setMaxVideoSize(1280, 720)
            )
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                val mediaItem = MediaItem.fromUri(
                    "https://test-streams.mux.dev/tos_ismc/main.m3u8"
                )
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    // Holds the live PlayerView so the video-size listener can force a
    // layout pass on it directly (belt-and-suspenders fix for SurfaceView/
    // TextureView layout-timing issues on quality change).
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // Force the PlayerView (and its internal AspectRatioFrameLayout)
                // to re-measure immediately instead of waiting for the next
                // natural layout pass, which is where stale-surface-size bugs
                // tend to come from on quality switches. RESIZE_MODE_FIT handles
                // the actual letterboxing math internally.
                playerViewRef?.requestLayout()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Full-screen black container. PlayerView's RESIZE_MODE_FIT scales the video
    // to fit inside this box while preserving its aspect ratio, so any leftover
    // space (top/bottom for wide video, left/right for narrow video) shows as
    // black bars against this background — standard letterbox/pillarbox behavior.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = View.inflate(ctx, R.layout.video_player, null)
                val playerView = view.findViewById<PlayerView>(R.id.custom_video_player)

                playerView.apply {
                    // XML already sets app:surface_type="texture_view", which avoids
                    // the SurfaceView stale-size/black-flash bug on some decoders.
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer

                    // XML sets use_controller="false"; enabling here so the built-in
                    // controller config below applies. Flip to false if/when you swap
                    // in a fully custom Compose control overlay.
                    useController = true
                    controllerAutoShow = true
                    controllerHideOnTouch = true
                    keepScreenOn = true

                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }

                playerViewRef = playerView
                playerView
            }
        )
    }
}