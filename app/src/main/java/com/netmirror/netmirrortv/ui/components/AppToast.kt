package com.netmirror.netmirrortv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

object ToastDuration {
    const val SHORT = 3000L
    const val MEDIUM = 5000L
    const val LONG = 8000L
    const val INDEFINITE = 0L
}

/**
 * Reusable light theme toast component with smooth slide/fade animations.
 * Layout: Logo/Icon on left, Title on top right, Message below title.
 * Duration can be decided at declaration time via [durationMs] (e.g. 3000L, ToastDuration.LONG, etc.).
 */
@Composable
fun AppToast(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    durationMs: Long = ToastDuration.MEDIUM,
    visible: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    icon: ImageVector? = Icons.Default.Warning,
    iconTint: Color = Color(0xFFD32F2F)
) {
    var isVisible by remember(visible, title, message) { mutableStateOf(visible) }

    LaunchedEffect(visible, title, message) {
        if (visible) {
            isVisible = true
            if (durationMs > 0) {
                delay(durationMs)
                isVisible = false
                onDismiss?.invoke()
            }
        } else {
            isVisible = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        color = Color(0xFF1C1C1E),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (message.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = message,
                            color = Color(0xFF444444),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
