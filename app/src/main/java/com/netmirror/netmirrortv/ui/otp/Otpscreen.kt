package com.netmirror.netmirrortv.ui.otp

import android.app.Activity
import android.app.Application
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.netmirror.netmirrortv.R
import com.netmirror.netmirrortv.model.OtpResponse
import com.netmirror.netmirrortv.navigation.Routes

private const val OTP_LENGTH = 6

@Composable
fun OtpScreen(
    navController: NavHostController,
    apiUrl: String
) {
    val context = LocalContext.current
    val viewModel: OtpViewModel = viewModel(
        factory = OtpViewModelFactory(context.applicationContext as Application, apiUrl)
    )

    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OtpEvent.NavigateHome -> {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.OTP) { inclusive = true }
                    }
                }
                OtpEvent.RefocusInput -> {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
        }
    }

    BackHandler { showExitDialog = true }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit App") },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // Centered Glass Card with OTP Code and Publisher Message Below
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "OTP VERIFICATION",
                        color = Color(0xFFE50914),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter 6-Digit Code",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Please enter the verification code sent to your device",
                        color = Color(0xFFA0A5B5),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Premium 6-Digit PIN Display & Hidden Input
                    OtpInputContainer(
                        otp = uiState.otp,
                        enabled = !uiState.isValidating && !uiState.success,
                        isError = uiState.error.isNotEmpty(),
                        isSuccess = uiState.success,
                        focusRequester = focusRequester,
                        onOtpChanged = viewModel::onOtpChanged,
                        onOpenKeyboard = {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status Messages (Success / Error / Loading)
                    StatusSection(uiState = uiState)

                    // Publisher Message & Image Content Directly Below the OTP Code
                    val response = uiState.response
                    val hasPubMessage = !response?.pub_msg.isNullOrEmpty() || !response?.pub_msg_img?.img.isNullOrEmpty()
                    if (hasPubMessage) {
                        Spacer(modifier = Modifier.height(14.dp))
                        PublisherMessage(response = response)
                    }
                }
            }

        }
    }
}

@Composable
private fun OtpInputContainer(
    otp: String,
    enabled: Boolean,
    isError: Boolean,
    isSuccess: Boolean,
    focusRequester: FocusRequester,
    onOtpChanged: (String) -> Unit,
    onOpenKeyboard: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val btnInteractionSource = remember { MutableInteractionSource() }
    val isBtnFocused by btnInteractionSource.collectIsFocusedAsState()

    val btnBorderColor = if (isBtnFocused) Color(0xFFE50914) else Color(0xFF3B4154)
    val btnBgColor = if (isBtnFocused) Color(0xFF321A20) else Color(0xFF232733)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null) {
                onOpenKeyboard()
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    onOpenKeyboard()
                    true
                } else {
                    false
                }
            }
    ) {
        // Hidden BasicTextField capturing keyboard & remote inputs
        BasicTextField(
            value = otp,
            onValueChange = onOtpChanged,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester),
            interactionSource = interactionSource
        )

        // Visual 6-Digit PIN Boxes
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(OTP_LENGTH) { index ->
                val char = otp.getOrNull(index)?.toString() ?: ""
                val isCurrentSlot = isFocused && (index == otp.length || (index == OTP_LENGTH - 1 && otp.length == OTP_LENGTH))

                val borderColor = when {
                    isError -> Color(0xFFFF4444)
                    isSuccess -> Color(0xFF4CAF50)
                    isCurrentSlot -> Color(0xFFE50914)
                    char.isNotEmpty() -> Color(0xFF4F566B)
                    else -> Color(0xFF2C303E)
                }

                val backgroundColor = when {
                    isError -> Color(0xFF2A1215)
                    isSuccess -> Color(0xFF132819)
                    isCurrentSlot -> Color(0xFF25181C)
                    char.isNotEmpty() -> Color(0xFF20232D)
                    else -> Color(0xFF1A1C24)
                }

                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .border(
                            width = if (isCurrentSlot || isError || isSuccess) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // TV Remote Action Button (Focusable button so DPAD users can click to open keyboard)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(btnBgColor)
            .border(if (isBtnFocused) 2.dp else 1.dp, btnBorderColor, RoundedCornerShape(30.dp))
            .focusable(interactionSource = btnInteractionSource)
            .clickable(interactionSource = btnInteractionSource, indication = null) {
                onOpenKeyboard()
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    onOpenKeyboard()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE50914))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "PRESS OK TO ENTER CODE",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun StatusSection(uiState: OtpUiState) {
    Box(
        modifier = Modifier.height(32.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            uiState.isValidating -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFFE50914),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Verifying code...",
                        color = Color(0xFFA0A5B5),
                        fontSize = 13.sp
                    )
                }
            }
            uiState.success -> {
                Text(
                    text = "✓ Verification Successful!",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            uiState.error.isNotEmpty() -> {
                Text(
                    text = uiState.error,
                    color = Color(0xFFFF4444),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PublisherMessage(response: OtpResponse?) {
    val pubMsg = response?.pub_msg
    val imgUrl = response?.pub_msg_img?.img
    if (pubMsg.isNullOrEmpty() && imgUrl.isNullOrEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101116))
            .border(1.dp, Color(0xFF2A2E3B), RoundedCornerShape(14.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!pubMsg.isNullOrEmpty()) {
                Text(
                    text = pubMsg,
                    color = runCatching {
                        Color(android.graphics.Color.parseColor(response?.pub_msg_color))
                    }.getOrDefault(Color.White),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (!imgUrl.isNullOrEmpty()) {
                if (!pubMsg.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                AsyncImage(
                    model = imgUrl,
                    contentDescription = "Publisher Announcement Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}