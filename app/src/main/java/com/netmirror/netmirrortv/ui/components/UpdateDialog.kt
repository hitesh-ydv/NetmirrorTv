package com.netmirror.netmirrortv.ui.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.netmirror.netmirrortv.model.UpdateMessageResponse
import kotlinx.coroutines.delay

/**
 * TV-optimized update modal dialog.
 * - Efficient static backdrop glow and layout (zero continuous animation overhead on TV chipsets).
 * - When isBtnOk == true: Focus is strictly locked and auto-focused onto the OK button; directional D-pad keys cannot leak to background.
 * - When isBtnOk == false: Entire background is blocked from receiving focus or key events; dialog traps all navigation.
 */
@Composable
fun UpdateDialog(
    updateData: UpdateMessageResponse,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val okFocusRequester = remember { FocusRequester() }
    val trapFocusRequester = remember { FocusRequester() }

    var isOkFocused by remember { mutableStateOf(false) }
    var isTrapFocused by remember { mutableStateOf(false) }

    // Supports "ok", "true", "1", "yes" case-insensitively
    val isBtnOk = remember(updateData.btnOk) {
        updateData.btnOk?.trim()?.lowercase().let {
            it == "ok" || it == "true" || it == "1" || it == "yes"
        }
    }

    var showExitDialog by remember { mutableStateOf(false) }

    // BackHandler: If isBtnOk, dismiss and return focus to content.
    // If update is mandatory, prompt exit confirmation.
    BackHandler(enabled = true) {
        if (showExitDialog) {
            showExitDialog = false
            runCatching { trapFocusRequester.requestFocus() }
        } else if (isBtnOk) {
            onDismiss()
        } else {
            showExitDialog = true
        }
    }

    // Auto-focus immediately and keep retrying until the target node is genuinely focused:
    // If isBtnOk -> auto-focus onto OK button
    // Else -> auto-focus onto dialog card to completely block background navigation
    LaunchedEffect(isBtnOk) {
        if (isBtnOk) {
            delay(50)
            repeat(20) {
                if (isOkFocused) return@LaunchedEffect
                runCatching { okFocusRequester.requestFocus() }
                delay(50)
            }
        } else {
            delay(50)
            repeat(20) {
                if (isTrapFocused) return@LaunchedEffect
                runCatching { trapFocusRequester.requestFocus() }
                delay(50)
            }
        }
    }

    // Fullscreen dark overlay scrim - intercepts and consumes all remote keys
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
            .focusProperties { canFocus = false }
            .onPreviewKeyEvent { event ->
                if (showExitDialog) {
                    // Handled by exit confirmation overlay
                    return@onPreviewKeyEvent false
                }

                // If button is supposed to be focused but isn't yet, re-focus on any key
                if (isBtnOk && !isOkFocused) {
                    runCatching { okFocusRequester.requestFocus() }
                }

                if (event.type == KeyEventType.KeyDown) {
                    // Back / Escape key
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        if (isBtnOk) {
                            onDismiss()
                        } else {
                            showExitDialog = true
                        }
                        return@onPreviewKeyEvent true
                    }

                    if (isBtnOk) {
                        // Enter / Center clicks
                        if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                            onDismiss()
                            return@onPreviewKeyEvent true
                        }
                        // Arrow keys force focus back to the OK button
                        if (event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                            event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                            event.key == Key.Tab
                        ) {
                            runCatching { okFocusRequester.requestFocus() }
                            return@onPreviewKeyEvent true
                        }
                    } else {
                        // Trap ALL keys when update is mandatory, completely blocking focus everywhere else
                        return@onPreviewKeyEvent true
                    }
                }
                // Trap all key up events as well when mandatory update
                !isBtnOk
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (isBtnOk) onDismiss()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Efficient static ambient glow behind the dialog card (zero GPU draw loop overhead)
        Box(
            modifier = Modifier
                .size(760.dp, 440.dp)
                .focusProperties { canFocus = false }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isBtnOk) Color(0xFF0284C7).copy(alpha = 0.20f) else Color(0xFFDC2626).copy(alpha = 0.20f),
                            if (isBtnOk) Color(0xFF4F46E5).copy(alpha = 0.10f) else Color(0xFF7C2D12).copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Modal Container Card
        val maxCardWidth = (updateData.img?.maxw ?: 840).coerceIn(400, 880).dp
        Box(
            modifier = Modifier
                .widthIn(max = maxCardWidth)
                .fillMaxWidth(0.80f)
                .then(
                    if (!isBtnOk) {
                        // When update is mandatory: Card captures focus and completely blocks navigation in all directions
                        Modifier
                            .focusRequester(trapFocusRequester)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .onFocusChanged { isTrapFocused = it.isFocused }
                            .focusable()
                    } else {
                        Modifier.focusProperties { canFocus = false }
                    }
                )
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = true,
                    onClick = {} // Consume click inside dialog card so scrim click is not fired
                )
                .focusProperties {
                    if (isBtnOk) canFocus = false
                }
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Update QR code / Banner Image
                val imageUrl = updateData.img?.url
                val maxH = (updateData.img?.maxh ?: 360).coerceAtLeast(200).dp

                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Update Banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxH)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                // Show OK button only if isBtnOk is true
                if (isBtnOk) {
                    Spacer(modifier = Modifier.height(20.dp))

                    UpdateDialogOkButton(
                        label = "OK",
                        focusRequester = okFocusRequester,
                        onFocusChanged = { isOkFocused = it },
                        onClick = onDismiss
                    )
                }
            }
        }

        // TV-optimized exit confirmation dialog when update is mandatory and user presses Back
        if (showExitDialog) {
            TvExitConfirmationModal(
                onConfirmExit = {
                    (context as? Activity)?.finishAffinity() ?: (context as? Activity)?.finish()
                },
                onCancel = {
                    showExitDialog = false
                    runCatching { trapFocusRequester.requestFocus() }
                }
            )
        }
    }
}

/**
 * OK button with TV focus lock:
 * Directional D-pad moves are strictly cancelled to prevent escaping to background views.
 */
@Composable
private fun UpdateDialogOkButton(
    label: String,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(110),
        label = "okBtnScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(110),
        label = "okBtnBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isFocused) Color.Black else Color.White,
        animationSpec = tween(110),
        label = "okBtnContent"
    )

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusProperties {
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .width(160.dp)
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(11.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(11.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            return@onPreviewKeyEvent true
                        }
                        Key.Back, Key.Escape -> {
                            onClick()
                            return@onPreviewKeyEvent true
                        }
                        // Stay locked on OK button - trap all directional arrow keys and tab
                        Key.DirectionUp, Key.DirectionDown,
                        Key.DirectionLeft, Key.DirectionRight,
                        Key.Tab -> {
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * TV-optimized modal dialog for confirming exit when mandatory update is active.
 * Manages D-pad focus smoothly between Cancel and Exit buttons without leaking focus.
 */
@Composable
private fun TvExitConfirmationModal(
    onConfirmExit: () -> Unit,
    onCancel: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }
    var isCancelFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        repeat(15) {
            if (isCancelFocused) return@LaunchedEffect
            runCatching { cancelFocusRequester.requestFocus() }
            delay(40)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .focusProperties { canFocus = false }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                    onCancel()
                    return@onPreviewKeyEvent true
                }
                false
            }
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
                    onClick = {} // Consume click inside dialog
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvActionButton(
                        label = "Cancel",
                        isDanger = false,
                        focusRequester = cancelFocusRequester,
                        modifier = Modifier.weight(1f),
                        onFocusChanged = { isCancelFocused = it },
                        onRight = { exitFocusRequester.requestFocus() },
                        onClick = onCancel
                    )
                    TvActionButton(
                        label = "Exit",
                        isDanger = true,
                        focusRequester = exitFocusRequester,
                        modifier = Modifier.weight(1f),
                        onLeft = { cancelFocusRequester.requestFocus() },
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
    modifier: Modifier = Modifier,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

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

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .height(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.20f),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
            .clickable(onClick = onClick)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionLeft -> {
                            if (onLeft != null) {
                                onLeft()
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionRight -> {
                            if (onRight != null) {
                                onRight()
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.DirectionUp, Key.DirectionDown -> return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .focusable(),
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
