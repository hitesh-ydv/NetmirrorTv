package com.netmirror.netmirrortv.ui.ott

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.netmirror.netmirrortv.utils.TvFocusManager
import kotlinx.coroutines.delay

import com.netmirror.netmirrortv.ui.components.AppToast

private val CARD_WIDTH = 200.dp
private val CARD_HEIGHT = 105.dp

@Composable
fun OttScreen(
    viewModel: OttViewModel = viewModel(),
    onNavigateToHome: () -> Unit = {}
) {

    val loading by viewModel.loading.collectAsState()
    val otts by viewModel.otts.collectAsState()
    val error by viewModel.error.collectAsState()

    val firstCardFocusRequester = remember {
        FocusRequester()
    }

    TvFocusManager.currentScreenContentRequester = firstCardFocusRequester

    LaunchedEffect(otts) {
        if (otts.isNotEmpty()) {
            delay(350)
            firstCardFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(start = 100.dp)
    ) {

        if (loading) {

            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )

        } else {

            val rows = remember(otts) {
                otts.chunked(3)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {

                rows.forEachIndexed { rowIndex, rowItems ->

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {

                        rowItems.forEachIndexed { columnIndex, item ->

                            OttCard(
                                image = item.img,
                                isFirstInRow = columnIndex == 0,
                                focusRequester =
                                    if (rowIndex == 0 && columnIndex == 0)
                                        firstCardFocusRequester
                                    else
                                        null,
                                onClick = {
                                    Log.d("OTT", "Clicked: ${item.value}")
                                    viewModel.saveSelectedOtt(item.value)
                                    onNavigateToHome()
                                }
                            )
                        }

                        repeat(3 - rowItems.size) {
                            Spacer(
                                modifier = Modifier.width(CARD_WIDTH)
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            AppToast(
                title = "Network Error",
                message = "OTT Page Loading Fail, Retry after some times.",
                durationMs = 60000L,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
private fun OttCard(
    image: String,
    isFirstInRow: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val cardFocusRequester = focusRequester ?: remember { FocusRequester() }

    var focused by remember {
        mutableStateOf(false)
    }

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        label = "scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (focused)
            Color.White
        else
            Color.Transparent,
        animationSpec = tween(220),
        label = "border"
    )

    Box(
        modifier = Modifier
            .focusRequester(cardFocusRequester)
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF202020))
            .border(
                width = 3.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    TvFocusManager.lastFocusedContentRequester = cardFocusRequester
                }
            }
            .onPreviewKeyEvent { event ->
                when {
                    isFirstInRow &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionLeft -> {
                        TvFocusManager.moveFocusToDrawer()
                        true
                    }
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter ||
                     event.key == Key.Enter ||
                     event.key == Key.NumPadEnter) -> {
                        Log.d("OTT", "CENTER PRESSED")
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable {
                onClick()
            }
            .focusable()
    ) {

        AsyncImage(
            model = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

    }
}