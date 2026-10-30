package com.netmirror.netmirrortv.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.netmirror.netmirrortv.R
import com.netmirror.netmirrortv.navigation.Routes
import com.netmirror.netmirrortv.network.BaseUrlProvider
import com.netmirror.netmirrortv.util.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val NetmirrorRed = Color(0xFFE50914)
private val PureBlack = Color(0xFF000000)

@Composable
fun SplashScreen(
    navController: NavHostController,
    viewModel: SplashViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Spring jump and entrance animatables
    val jumpOffsetY = remember { Animatable(-320f) }
    val logoAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    // Kick off startup server checks
    LaunchedEffect(Unit) {
        viewModel.checkServer()
    }

    // Spring drop from top to center
    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = LinearEasing)
            )
        }
        launch {
            jumpOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Subtle heartbeat pulse rhythm on the logo
    val transition = rememberInfiniteTransition(label = "logoHeartbeatTransition")
    val heartbeatScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1500
                1.0f at 0 using FastOutSlowInEasing
                1.050f at 160 using FastOutSlowInEasing  // First systolic beat
                1.010f at 300 using FastOutSlowInEasing  // Dip
                1.035f at 440 using FastOutSlowInEasing  // Second diastolic beat
                1.0f at 660 using FastOutSlowInEasing    // Baseline return
                1.0f at 1500                             // Rest period before next beat
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "logoHeartbeatScale"
    )

    // Smooth navigation to Home screen on success
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            delay(180)
            exitAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )

            val hasToken = !TokenStore.getUserToken(context).isNullOrBlank()
            if (hasToken) {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.SPLASH) { this.inclusive = true }
                }
            } else {
                val baseUrl = BaseUrlProvider.getBaseUrl()
                navController.navigate(Routes.otp(baseUrl)) {
                    popUpTo(Routes.SPLASH) { this.inclusive = true }
                }
            }
        }
    }

    // Fullscreen Dark Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .graphicsLayer {
                alpha = exitAlpha.value
            }
    ) {
        // 1. Centered Logo with Spring Jump + Heartbeat Pulse (no glow)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationY = jumpOffsetY.value
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "Netmirror TV Logo",
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = heartbeatScale
                        scaleY = heartbeatScale
                        alpha = logoAlpha.value
                    }
            )
        }

        // 2. Bottom section: Fade-Arc Spinner OR Retry UI on Failure
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.error != null) {
                SplashRetryCard(
                    onRetry = { viewModel.retry() }
                )
            } else {
                FadeArcSpinner(
                    size = 52.dp,
                    strokeWidth = 6.5.dp,
                    sweepAngle = 260f
                )
            }
        }
    }
}

/**
 * Premium Fade-Arc Spinner matching reference design:
 * - Thick, bold circular arc stroke.
 * - Smooth continuous sweep gradient fading from completely invisible (0% alpha) to 100% solid color.
 * - Solid rounded leading head dot seamlessly leading the arc.
 * - Clean floating appearance on pitch black background (no distracting track ring).
 * - Continuous 360-degree rotation.
 */
@Composable
fun FadeArcSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    strokeWidth: Dp = 6.5.dp,
    sweepAngle: Float = 260f,
    color: Color = NetmirrorRed
) {
    val transition = rememberInfiniteTransition(label = "fadeArcSpinnerTransition")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fadeArcRotation"
    )

    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val radius = (this.size.minDimension - strokePx) / 2f
        val diameter = radius * 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val arcSize = Size(diameter, diameter)
        val arcTopLeft = Offset(center.x - radius, center.y - radius)

        rotate(degrees = rotation, pivot = center) {
            val sweepFraction = sweepAngle / 360f

            // Smooth continuous gradient from 0% opacity to 100% solid color at the head
            val sweepBrush = Brush.sweepGradient(
                0.0f to Color.Transparent,
                sweepFraction * 0.20f to color.copy(alpha = 0.05f),
                sweepFraction * 0.45f to color.copy(alpha = 0.25f),
                sweepFraction * 0.70f to color.copy(alpha = 0.60f),
                sweepFraction * 0.90f to color.copy(alpha = 0.90f),
                sweepFraction to color,
                (sweepFraction + 0.001f).coerceAtMost(0.999f) to Color.Transparent,
                1.0f to Color.Transparent,
                center = center
            )

            // Draw the fade arc
            drawArc(
                brush = sweepBrush,
                startAngle = 0f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                topLeft = arcTopLeft,
                size = arcSize
            )

            // Solid rounded head dot at the leading tip of the fade arc
            val headAngleRad = Math.toRadians(sweepAngle.toDouble())
            val dotCenter = Offset(
                x = center.x + radius * cos(headAngleRad).toFloat(),
                y = center.y + radius * sin(headAngleRad).toFloat()
            )

            drawCircle(
                color = color,
                radius = strokePx / 2f,
                center = dotCenter
            )
        }
    }
}

/**
 * TV D-Pad Remote Compatible Retry UI.
 *
 * Appears seamlessly if API/server calls encounter an error, auto-focuses the
 * retry button so the user can simply press 'OK' on their remote to reconnect.
 */
@Composable
private fun SplashRetryCard(
    onRetry: () -> Unit
) {
    val retryFocusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Auto-focus on appearance for immediate TV remote response
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { retryFocusRequester.requestFocus() }
    }

    val buttonScale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "retryScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = "Connection Failed",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Connection Failed",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Remote-control compatible Retry Button
        Box(
            modifier = Modifier
                .focusRequester(retryFocusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isFocused) NetmirrorRed else Color.White.copy(alpha = 0.12f)
                )
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRetry
                )
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        if (keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        ) {
                            onRetry()
                            return@onKeyEvent true
                        }
                    }
                    false
                }
                .padding(horizontal = 22.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Retry",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
