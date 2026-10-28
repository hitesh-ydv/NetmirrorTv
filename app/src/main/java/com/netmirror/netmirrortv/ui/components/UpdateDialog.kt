package com.netmirror.netmirrortv.ui.components

import android.app.Activity
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.netmirror.netmirrortv.model.UpdateMessageResponse
import com.netmirror.netmirrortv.utils.ApkUpdateInstaller
import com.netmirror.netmirrortv.utils.TvFocusManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

sealed interface UpdateDownloadState {
    object Idle : UpdateDownloadState
    data class Downloading(val percent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState
    object ReadyToInstall : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

@Composable
fun UpdateDialog(
    updateData: UpdateMessageResponse,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val updateFocusRequester = remember { FocusRequester() }
    val installerFocusRequester = remember { FocusRequester() }
    val okFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val downloadFocusRequester = remember { FocusRequester() }

    var isUpdateFocused by remember { mutableStateOf(false) }
    var isInstallerFocused by remember { mutableStateOf(false) }
    var isOkFocused by remember { mutableStateOf(false) }
    var isRetryFocused by remember { mutableStateOf(false) }
    var isDownloadFocused by remember { mutableStateOf(false) }

    var lastFocusedRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var resumeFocusTrigger by remember { mutableStateOf(0) }

    // Set once the dialog starts closing, so the focus keeper stops fighting with the
    // screen behind it (otherwise it steals focus back during the exit animation).
    var isClosing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            onDismiss()
        }
    }

    // Lock background components from stealing focus while UpdateDialog is open
    DisposableEffect(Unit) {
        TvFocusManager.isUpdateDialogOpen = true
        onDispose {
            TvFocusManager.isUpdateDialogOpen = false
        }
    }

    // Regain focus when returning to app (e.g., from Unknown Sources permission settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeFocusTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Supports "ok", "true", "1", "yes" case-insensitively. Anything else (e.g. "false", "0", null) = mandatory.
    val isBtnOk = remember(updateData.btnOk) {
        updateData.btnOk?.trim()?.lowercase().let {
            it == "ok" || it == "true" || it == "1" || it == "yes"
        }
    }

    // Resolves download URL from response or fallback Netmirror download URL
    val apkDownloadUrl = remember(updateData) {
        updateData.apkUrl?.takeIf { it.isNotBlank() }
            ?: updateData.apkLink?.takeIf { it.isNotBlank() }
            ?: updateData.downloadUrl?.takeIf { it.isNotBlank() }
            ?: updateData.apk?.takeIf { it.isNotBlank() }
            ?: updateData.url?.takeIf { it.isNotBlank() }
            ?: updateData.link?.takeIf { it.isNotBlank() }
            ?: ""
    }

    var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Start download action
    val startDownload: () -> Unit = {
        android.util.Log.d("UpdateDialog", "startDownload invoked! apkDownloadUrl='$apkDownloadUrl'")
        if (apkDownloadUrl.isBlank()) {
            android.util.Log.e("UpdateDialog", "apkDownloadUrl is blank! updateData=$updateData")
            downloadState = UpdateDownloadState.Error("Update link unavailable. Please try again later.")
        } else {
            downloadState = UpdateDownloadState.Downloading(percent = 0, downloadedBytes = 0L, totalBytes = 0L)
            coroutineScope.launch {
                ApkUpdateInstaller.downloadAndInstall(
                    context = context,
                    apkUrl = apkDownloadUrl,
                    onProgress = { percent, downloadedBytes, totalBytes ->
                        if (percent >= 100) {
                            downloadState = UpdateDownloadState.ReadyToInstall
                        } else {
                            downloadState = UpdateDownloadState.Downloading(percent, downloadedBytes, totalBytes)
                        }
                    },
                    onError = { message ->
                        downloadState = UpdateDownloadState.Error(message)
                    }
                )
            }
        }
    }

    // Reset stale focus flags when download state transitions
    LaunchedEffect(downloadState) {
        when (downloadState) {
            is UpdateDownloadState.Idle -> {
                isDownloadFocused = false
                isInstallerFocused = false
                isRetryFocused = false
            }
            is UpdateDownloadState.Downloading -> {
                isUpdateFocused = false
                isOkFocused = false
                isInstallerFocused = false
                isRetryFocused = false
            }
            is UpdateDownloadState.ReadyToInstall -> {
                isUpdateFocused = false
                isOkFocused = false
                isDownloadFocused = false
                isRetryFocused = false
            }
            is UpdateDownloadState.Error -> {
                isUpdateFocused = false
                isDownloadFocused = false
                isInstallerFocused = false
            }
        }
    }

    // True only if a button that exists in the CURRENT state has focus
    // (ignores stale flags from buttons that already left the composition).
    val isDialogFocusedState = remember {
        derivedStateOf {
            when (downloadState) {
                is UpdateDownloadState.Idle -> isUpdateFocused || (isBtnOk && isOkFocused)
                is UpdateDownloadState.Downloading -> isDownloadFocused
                is UpdateDownloadState.ReadyToInstall -> isInstallerFocused
                is UpdateDownloadState.Error -> isRetryFocused || (isBtnOk && isOkFocused)
            }
        }
    }
    val isDialogFocused by isDialogFocusedState

    // Which node should get focus right now. Installer button is ALWAYS forced in ReadyToInstall.
    // "Later/Close" is only a valid target when btnOk = true and we are in Idle/Error.
    fun resolveFocusTarget(): FocusRequester {
        val last = lastFocusedRequester
        return when (downloadState) {
            is UpdateDownloadState.Idle ->
                if (isBtnOk && last === okFocusRequester) okFocusRequester else updateFocusRequester
            is UpdateDownloadState.Downloading -> downloadFocusRequester
            is UpdateDownloadState.ReadyToInstall -> installerFocusRequester
            is UpdateDownloadState.Error ->
                if (isBtnOk && last === okFocusRequester) okFocusRequester else retryFocusRequester
        }
    }

    // BackHandler:
    //  - exit dialog open        -> close it (focus returns to Update / Installer button)
    //  - btnOk = true            -> hide the dialog (not while a download is running)
    //  - btnOk = false           -> show exit confirmation
    BackHandler(enabled = true) {
        when {
            showExitDialog -> {
                showExitDialog = false
                coroutineScope.launch {
                    repeat(10) { attempt ->
                        if (isDialogFocused) return@launch
                        runCatching { resolveFocusTarget().requestFocus() }
                        delay(20L * (attempt + 1))
                    }
                }
            }
            isBtnOk && downloadState is UpdateDownloadState.Downloading -> Unit // keep downloading
            isBtnOk -> dismiss()
            else -> showExitDialog = true
        }
    }

    val isWindowFocused = LocalWindowInfo.current.isWindowFocused

    // Initial immediate focus grabber on show
    LaunchedEffect(Unit) {
        repeat(15) { attempt ->
            if (isDialogFocused || isClosing) return@LaunchedEffect
            runCatching { resolveFocusTarget().requestFocus() }
            delay(20L)
        }
    }

    // Focus keeper: grabs focus on open, on state change, after the exit dialog closes,
    // when the app resumes (e.g. back from "Install unknown apps" settings) and when the
    // window regains focus. Keeps watching continuously so focus can never be lost.
    LaunchedEffect(downloadState, showExitDialog, resumeFocusTrigger, isWindowFocused) {
        if (showExitDialog) return@LaunchedEffect
        var attempt = 0
        while (!isClosing) {
            if (!isDialogFocused) {
                runCatching { resolveFocusTarget().requestFocus() }
            }
            delay(if (attempt < 10) 25L else 50L)
            attempt++
        }
    }

    // Fullscreen dark overlay scrim
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
            // Intercept any D-pad key on the dialog container to prevent it escaping to background
            .onPreviewKeyEvent { event ->
                val isDpad = event.key == Key.DirectionLeft ||
                        event.key == Key.DirectionRight ||
                        event.key == Key.DirectionUp ||
                        event.key == Key.DirectionDown ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN

                if (isDpad && !isDialogFocused && !showExitDialog) {
                    runCatching { resolveFocusTarget().requestFocus() }
                    return@onPreviewKeyEvent true
                }
                false
            }
            // Focus trap: if focus ever leaves the dialog, pull it straight back
            .onFocusChanged { focusState ->
                if (!focusState.hasFocus && !showExitDialog && !isClosing) {
                    coroutineScope.launch {
                        while (!isDialogFocused && !showExitDialog && !isClosing) {
                            runCatching { resolveFocusTarget().requestFocus() }
                            delay(25L)
                        }
                    }
                }
            }
            // Scrim must NEVER be a focus target. pointerInput (not clickable) so it only
            // reacts to touch/mouse and can't grab D-pad focus.
            .pointerInput(isBtnOk) {
                detectTapGestures {
                    if (isBtnOk && downloadState !is UpdateDownloadState.Downloading) {
                        dismiss()
                    } else {
                        runCatching { resolveFocusTarget().requestFocus() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // API max dimensions parsed strictly in percentage or float fraction (f)
        val widthFraction = remember(updateData.img?.maxw) {
            parseDimensionFraction(updateData.img?.maxw, defaultFraction = 0.80f)
        }
        val heightFraction = remember(updateData.img?.maxh, updateData.img?.maxh2) {
            val rawH = updateData.img?.maxh ?: updateData.img?.maxh2
            parseDimensionFraction(rawH, defaultFraction = 0.82f)
        }

        val cardPaddingH = 24.dp
        val cardPaddingV = 22.dp
        val imageControlsGap = 16.dp
        val controlsReserve = 100.dp      // Reserve space for controls
        val minCardWidth = 380.dp         // Minimum width for buttons

        val imageUrl = updateData.img?.url
        val hasImage = !imageUrl.isNullOrBlank()

        val maxImageW = (maxWidth - cardPaddingH * 2).coerceAtLeast(0.dp)
        val maxImageH = (maxHeight - cardPaddingV * 2 - controlsReserve - imageControlsGap)
            .coerceAtLeast(0.dp)

        val imageW = (maxWidth * widthFraction).coerceAtMost(maxImageW)
        val imageH = (maxHeight * heightFraction).coerceAtMost(maxImageH)

        val cardWidth = if (hasImage) {
            (imageW + cardPaddingH * 2).coerceAtLeast(minCardWidth).coerceAtMost(maxWidth)
        } else {
            minCardWidth.coerceAtMost(maxWidth)
        }

        // Glow effect
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.25f
                        scaleY = 1.25f
                    }
                    .focusProperties { canFocus = false }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (isBtnOk) Color(0xFF0284C7).copy(alpha = 0.20f) else Color(0xFFE50914).copy(alpha = 0.22f),
                                Color(0xFF220508).copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Modal card
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E1E26),
                                Color(0xFF121217)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .shadow(elevation = 32.dp, shape = RoundedCornerShape(22.dp))
                    // Card must not be a focus target either (touch-only tap swallow)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            runCatching { resolveFocusTarget().requestFocus() }
                        }
                    }
                    .animateContentSize(animationSpec = tween(250))
                    .width(cardWidth)
                    .wrapContentHeight()
                    .padding(horizontal = cardPaddingH, vertical = cardPaddingV)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Banner image
                    if (hasImage) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Update Banner",
                            modifier = Modifier
                                .size(width = imageW, height = imageH)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(imageControlsGap))
                    }

                    // Column 2: Dedicated Column for Download Progress, Update & Later Buttons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        when (val state = downloadState) {
                            is UpdateDownloadState.Idle -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        modifier = Modifier.focusGroup(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Primary "Update Now" Button
                                        TvUpdateButton(
                                            label = "UPDATE NOW",
                                            focusRequester = updateFocusRequester,
                                            rightFocusRequester = if (isBtnOk) okFocusRequester else null,
                                            leftFocusRequester = null,
                                            onFocusChanged = { focused ->
                                                isUpdateFocused = focused
                                                if (focused) lastFocusedRequester = updateFocusRequester
                                            },
                                            onClick = startDownload
                                        )

                                        // Optional "Later" / "OK" Button
                                        if (isBtnOk) {
                                            TvLaterButton(
                                                label = "LATER",
                                                focusRequester = okFocusRequester,
                                                leftFocusRequester = updateFocusRequester,
                                                rightFocusRequester = null,
                                                onFocusChanged = { focused ->
                                                    isOkFocused = focused
                                                    if (focused) lastFocusedRequester = okFocusRequester
                                                },
                                                onClick = dismiss
                                            )
                                        }
                                    }
                                }
                            }

                            is UpdateDownloadState.Downloading -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Focus anchor so focus stays trapped inside the dialog while downloading
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusProperties {
                                                left = FocusRequester.Cancel
                                                right = FocusRequester.Cancel
                                                up = FocusRequester.Cancel
                                                down = FocusRequester.Cancel
                                            }
                                            .focusRequester(downloadFocusRequester)
                                            .onFocusChanged {
                                                val focused = it.isFocused || it.hasFocus
                                                isDownloadFocused = focused
                                                if (focused) lastFocusedRequester = downloadFocusRequester
                                            }
                                            .onPreviewKeyEvent { event ->
                                                val isDpad = event.key == Key.DirectionLeft ||
                                                        event.key == Key.DirectionRight ||
                                                        event.key == Key.DirectionUp ||
                                                        event.key == Key.DirectionDown ||
                                                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                                                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
                                                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
                                                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                                                isDpad
                                            }
                                            .focusable(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DownloadProgressSection(
                                            percent = state.percent,
                                            downloadedBytes = state.downloadedBytes,
                                            totalBytes = state.totalBytes
                                        )
                                    }
                                }
                            }

                            is UpdateDownloadState.ReadyToInstall -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFE50914),
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Ready to install! Confirm on screen prompt...",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    TvUpdateButton(
                                        label = "OPEN INSTALLER",
                                        focusRequester = installerFocusRequester,
                                        rightFocusRequester = null,
                                        leftFocusRequester = null,
                                        onFocusChanged = { focused ->
                                            isInstallerFocused = focused
                                            if (focused) lastFocusedRequester = installerFocusRequester
                                        },
                                        onClick = {
                                            val apkFile = File(File(context.cacheDir, "updates"), "NetmirrorTV_update.apk")
                                            ApkUpdateInstaller.installApk(context, apkFile)
                                        }
                                    )
                                }
                            }

                            is UpdateDownloadState.Error -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = state.message,
                                        color = Color(0xFFFF4444),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.focusGroup(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TvUpdateButton(
                                            label = "RETRY DOWNLOAD",
                                            focusRequester = retryFocusRequester,
                                            rightFocusRequester = if (isBtnOk) okFocusRequester else null,
                                            leftFocusRequester = null,
                                            onFocusChanged = { focused ->
                                                isRetryFocused = focused
                                                if (focused) lastFocusedRequester = retryFocusRequester
                                            },
                                            onClick = startDownload
                                        )

                                        if (isBtnOk) {
                                            TvLaterButton(
                                                label = "CLOSE",
                                                focusRequester = okFocusRequester,
                                                leftFocusRequester = retryFocusRequester,
                                                rightFocusRequester = null,
                                                onFocusChanged = { focused ->
                                                    isOkFocused = focused
                                                    if (focused) lastFocusedRequester = okFocusRequester
                                                },
                                                onClick = dismiss
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

        // TV-optimized exit confirmation dialog
        if (showExitDialog) {
            TvExitConfirmationModal(
                onConfirmExit = {
                    (context as? Activity)?.finishAffinity() ?: (context as? Activity)?.finish()
                },
                onCancel = {
                    showExitDialog = false
                    coroutineScope.launch {
                        repeat(10) { attempt ->
                            if (isDialogFocused) return@launch
                            runCatching { resolveFocusTarget().requestFocus() }
                            delay(20L * (attempt + 1))
                        }
                    }
                }
            )
        }
    }
}

/**
 * Animated Progress Bar with Percentage and Downloaded MB counters.
 */
@Composable
private fun DownloadProgressSection(
    percent: Int,
    downloadedBytes: Long,
    totalBytes: Long
) {
    val progressFloat = (percent.coerceIn(0, 100) / 100f)

    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Downloading Update...",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = if (percent >= 0) "$percent%" else "Downloading...",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF262A38))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFloat)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFF3344), Color(0xFFE50914))
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Byte count readout (e.g. 14.2 MB / 28.5 MB)
        if (totalBytes > 0) {
            val downloadedMb = downloadedBytes / (1024f * 1024f)
            val totalMb = totalBytes / (1024f * 1024f)
            Text(
                text = String.format(Locale.US, "%.1f MB / %.1f MB", downloadedMb, totalMb),
                color = Color(0xFFA0A5B5),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * TV D-Pad Remote Focusable "UPDATE NOW" Button in Netmirror Red.
 * Uses focusProperties to lock focus horizontally and prevent focus escaping vertically.
 */
@Composable
private fun TvUpdateButton(
    label: String,
    focusRequester: FocusRequester,
    rightFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionFocused by interactionSource.collectIsFocusedAsState()
    var isNodeFocused by remember { mutableStateOf(false) }
    val isFocused = isInteractionFocused || isNodeFocused

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "updateBtnScale"
    )

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .focusProperties {
                left = leftFocusRequester ?: FocusRequester.Cancel
                right = rightFocusRequester ?: FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                val focused = it.isFocused || it.hasFocus
                isNodeFocused = focused
                onFocusChanged(focused)
            }
            .onPreviewKeyEvent { event ->
                val isConfirm = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_SELECT

                if (isConfirm) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        android.util.Log.d("UpdateDialog", "TvUpdateButton onClick triggered by confirm key")
                        onClick()
                    }
                    return@onPreviewKeyEvent true
                }

                val isDpadLeft = event.key == Key.DirectionLeft ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT
                val isDpadRight = event.key == Key.DirectionRight ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                val isDpadUp = event.key == Key.DirectionUp ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                val isDpadDown = event.key == Key.DirectionDown ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN

                if (isDpadLeft) {
                    if (event.type == KeyEventType.KeyDown && leftFocusRequester != null) {
                        runCatching { leftFocusRequester.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isDpadRight) {
                    if (event.type == KeyEventType.KeyDown && rightFocusRequester != null) {
                        runCatching { rightFocusRequester.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isDpadUp || isDpadDown) {
                    return@onPreviewKeyEvent true
                }

                false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .height(44.dp)
            .widthIn(min = 160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(Color(0xFFE50914), shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = shape
            )
            .clip(shape)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * TV Focusable "LATER" / "CANCEL" Button.
 * Locks focus horizontally to the left button and prevents focus escaping.
 */
@Composable
private fun TvLaterButton(
    label: String,
    focusRequester: FocusRequester,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionFocused by interactionSource.collectIsFocusedAsState()
    var isNodeFocused by remember { mutableStateOf(false) }
    val isFocused = isInteractionFocused || isNodeFocused

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "laterBtnScale"
    )

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .focusProperties {
                left = leftFocusRequester ?: FocusRequester.Cancel
                right = rightFocusRequester ?: FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                val focused = it.isFocused || it.hasFocus
                isNodeFocused = focused
                onFocusChanged(focused)
            }
            .onPreviewKeyEvent { event ->
                val isConfirm = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_SELECT

                if (isConfirm) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        android.util.Log.d("UpdateDialog", "TvLaterButton onClick triggered by confirm key")
                        onClick()
                    }
                    return@onPreviewKeyEvent true
                }

                val isDpadLeft = event.key == Key.DirectionLeft ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT
                val isDpadRight = event.key == Key.DirectionRight ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                val isDpadUp = event.key == Key.DirectionUp ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                val isDpadDown = event.key == Key.DirectionDown ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN

                if (isDpadLeft) {
                    if (event.type == KeyEventType.KeyDown && leftFocusRequester != null) {
                        runCatching { leftFocusRequester.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isDpadRight) {
                    if (event.type == KeyEventType.KeyDown && rightFocusRequester != null) {
                        runCatching { rightFocusRequester.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isDpadUp || isDpadDown) {
                    return@onPreviewKeyEvent true
                }

                false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .height(44.dp)
            .widthIn(min = 120.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                if (isFocused) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
                shape
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.20f),
                shape = shape
            )
            .clip(shape)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

/**
 * TV-optimized modal dialog for confirming exit when mandatory update is active.
 */
@Composable
private fun TvExitConfirmationModal(
    onConfirmExit: () -> Unit,
    onCancel: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }
    var isCancelFocused by remember { mutableStateOf(false) }
    var isExitFocused by remember { mutableStateOf(false) }
    var lastFocusedRequester by remember { mutableStateOf(cancelFocusRequester) }

    val isModalFocused = isCancelFocused || isExitFocused

    // Active focus keeper for exit modal: keeps focus locked on Cancel / Exit buttons
    LaunchedEffect(Unit) {
        var attempt = 0
        while (true) {
            if (!isModalFocused) {
                runCatching { lastFocusedRequester.requestFocus() }
            }
            delay(if (attempt < 10) 25L else 50L)
            attempt++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .onPreviewKeyEvent { event ->
                val isDpad = event.key == Key.DirectionLeft ||
                        event.key == Key.DirectionRight ||
                        event.key == Key.DirectionUp ||
                        event.key == Key.DirectionDown ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN

                if (isDpad && !isModalFocused) {
                    runCatching { lastFocusedRequester.requestFocus() }
                    return@onPreviewKeyEvent true
                }
                false
            }
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .focusProperties { canFocus = false }
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1E1E26))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                .shadow(24.dp, RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        runCatching { cancelFocusRequester.requestFocus() }
                    }
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Exit NetMirror TV",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "A critical update is required to stream. Are you sure you want to exit?",
                    color = Color(0xFFD4D4D8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvActionButton(
                        label = "Cancel",
                        isDanger = false,
                        focusRequester = cancelFocusRequester,
                        leftFocusRequester = null,
                        rightFocusRequester = exitFocusRequester,
                        modifier = Modifier.weight(1f),
                        onFocusChanged = {
                            isCancelFocused = it
                            if (it) lastFocusedRequester = cancelFocusRequester
                        },
                        onClick = onCancel
                    )
                    TvActionButton(
                        label = "Exit",
                        isDanger = true,
                        focusRequester = exitFocusRequester,
                        leftFocusRequester = cancelFocusRequester,
                        rightFocusRequester = null,
                        modifier = Modifier.weight(1f),
                        onFocusChanged = {
                            isExitFocused = it
                            if (it) lastFocusedRequester = exitFocusRequester
                        },
                        onClick = onConfirmExit
                    )
                }
            }
        }
    }
}

/**
 * TV action button used in modal dialogs.
 */
@Composable
private fun TvActionButton(
    label: String,
    isDanger: Boolean,
    focusRequester: FocusRequester,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionFocused by interactionSource.collectIsFocusedAsState()
    var isNodeFocused by remember { mutableStateOf(false) }
    val isFocused = isInteractionFocused || isNodeFocused

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(110),
        label = "actionBtnScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused && isDanger -> Color(0xFFEF4444)
            isFocused -> Color.White
            isDanger -> Color(0xFFEF4444).copy(alpha = 0.20f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        animationSpec = tween(110),
        label = "actionBtnBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused && isDanger -> Color.White
            isFocused -> Color.Black
            isDanger -> Color(0xFFEF4444)
            else -> Color.White
        },
        animationSpec = tween(110),
        label = "actionBtnContent"
    )

    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .focusProperties {
                left = leftFocusRequester ?: FocusRequester.Cancel
                right = rightFocusRequester ?: FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                val focused = it.isFocused || it.hasFocus
                isNodeFocused = focused
                onFocusChanged?.invoke(focused)
            }
            .onPreviewKeyEvent { event ->
                val isConfirm = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_SELECT

                if (isConfirm) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        android.util.Log.d("UpdateDialog", "TvActionButton onClick triggered by confirm key")
                        onClick()
                    }
                    return@onPreviewKeyEvent true
                }

                val isDpadLeft = event.key == Key.DirectionLeft ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT
                val isDpadRight = event.key == Key.DirectionRight ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                val isDpadUp = event.key == Key.DirectionUp ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                val isDpadDown = event.key == Key.DirectionDown ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN

                if (isDpadLeft) {
                    if (event.type == KeyEventType.KeyDown && leftFocusRequester != null) {
                        runCatching { leftFocusRequester.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isDpadRight) {
                    if (event.type == KeyEventType.KeyDown && rightFocusRequester != null) {
                        runCatching { rightFocusRequester.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isDpadUp || isDpadDown) {
                    return@onPreviewKeyEvent true
                }

                false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .height(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(backgroundColor, shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.20f),
                shape = shape
            )
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Safely parses API dimension values (e.g. 80, 80.0, "80%", 0.8f, "0.8")
 * into a percentage fraction (0.0f - 1.0f).
 */
internal fun parseDimensionFraction(raw: Any?, defaultFraction: Float): Float {
    if (raw == null) return defaultFraction
    val str = raw.toString().trim().removeSuffix("%")
    val num = str.toFloatOrNull() ?: return defaultFraction
    return when {
        num <= 0f -> defaultFraction
        num <= 1.0f -> num
        num <= 100f -> num / 100f
        else -> (num / 1000f).coerceIn(0.1f, 1.0f)
    }
}