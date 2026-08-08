package com.netmirror.netmirrortv.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.netmirror.netmirrortv.R
import com.netmirror.netmirrortv.navigation.Routes
import com.netmirror.netmirrortv.network.BaseUrlProvider
import com.netmirror.netmirrortv.util.TokenStore
import kotlinx.coroutines.launch

// Static brushes — allocated once per process, never touched by the
// animation loop. Only opacity/scale/position are animated on top of these.
private val GlowBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF414141).copy(alpha = 0.35f), Color.Transparent)
)

@Composable
fun SplashScreen(
    navController: NavHostController,
    viewModel: SplashViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // API call sirf ek baar
    LaunchedEffect(Unit) {
        viewModel.checkServer()
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            val hasToken = !TokenStore.getUserToken(context).isNullOrBlank()

            if (hasToken) {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            } else {
                val baseUrl = BaseUrlProvider.getBaseUrl()
                navController.navigate(Routes.otp(baseUrl)) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }
    }


    val transition = rememberInfiniteTransition(label = "splashLoop")

    val breatheScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(contentAlignment = Alignment.Center) {
                GlowPulse(breatheScale)
                BreathingLogo(breatheScale = breatheScale)
            }

            Spacer(modifier = Modifier.height(48.dp))

            DotsLoader()
        }
    }
}

/**
 * Soft ambient glow behind the logo, pulsing in lockstep with the logo's
 * own breathing scale (same source value, so the two can never drift out
 * of sync). Gives the splash some depth instead of a flat logo-on-black —
 * this single touch is most of what separates a "real OTT app" splash
 * from a placeholder screen.
 */
@Composable
private fun GlowPulse(breatheScale: Float) {
    val breatheT = (breatheScale - 1f) / 0.05f // 0f..1f across the breathing range

    Box(
        modifier = Modifier
            .size(300.dp)
            .clip(CircleShape)
            .background(GlowBrush)
            .graphicsLayer {
                scaleX = breatheScale
                scaleY = breatheScale
                alpha = 0.18f + 0.14f * breatheT
            }
    )
}

@Composable
private fun BreathingLogo(breatheScale: Float) {
    val entranceScale = remember { Animatable(0.85f) }
    val entranceAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
        launch {
            entranceAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = LinearEasing)
            )
        }
    }

    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "Logo",
        modifier = Modifier
            .size(220.dp)
            .graphicsLayer {
                val combinedScale = entranceScale.value * breatheScale
                scaleX = combinedScale
                scaleY = combinedScale
                alpha = entranceAlpha.value
            }
    )
}

@Composable
private fun DotsLoader(
    dotCount: Int = 5,
    dotSize: Dp = 10.dp,
    spacing: Dp = 12.dp
) {
    val transition = rememberInfiniteTransition(label = "dotsLoader")
    val cycleDuration = 1000
    val stagger = cycleDuration / dotCount

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val t by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = cycleDuration
                        0f at 0
                        1f at cycleDuration / 2
                        0f at cycleDuration
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * stagger)
                ),
                label = "dotPulse$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        val scale = 0.6f + 0.4f * t
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.35f + 0.65f * t
                    }
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}