package com.netmirror.netmirrortv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * Simple, premium, and lightweight TV Toast notification.
 * Combines dark glass styling, clean typography, and smooth in/out animations.
 */
@Composable
fun AppToast(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    durationMs: Long = ToastDuration.MEDIUM,
    visible: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    icon: ImageVector? = Icons.Rounded.WarningAmber,
    accentColor: Color = Color(0xFFE50914),
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
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(300)) + scaleIn(initialScale = 0.90f, animationSpec = tween(350, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(280, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(250)) + scaleOut(targetScale = 0.90f, animationSpec = tween(280, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        val pillShape = RoundedCornerShape(28.dp)

        Box(
            modifier = Modifier
                .shadow(
                    elevation = 16.dp,
                    shape = pillShape,
                    spotColor = Color.Black.copy(alpha = 0.6f)
                )
                .clip(pillShape)
                .background(Color(0xF014161E))
                .border(
                    width = 1.dp,
                    color = Color(0xFF282C3A),
                    shape = pillShape
                )
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .widthIn(min = 260.dp, max = 500.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                }

                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (message.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = message,
                            color = Color(0xFFA0A5B5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
