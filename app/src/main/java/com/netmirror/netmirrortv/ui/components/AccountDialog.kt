package com.netmirror.netmirrortv.ui.components

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.netmirror.netmirrortv.util.TokenStore
import com.netmirror.netmirrortv.utils.AppSession
import com.netmirror.netmirrortv.utils.TvFocusManager
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Screen navigation states within AccountDialog.
 */
enum class AccountDialogScreen {
    PROFILES,
    SETTINGS,
    WARNING,
    HELP
}

/**
 * Centered, focus-locked Android TV Profiles & Account Management Dialog.
 * Closes on a SINGLE Back press, and restores focus to the drawer Account logo.
 */
@Composable
fun AccountDialog(
    accLogoUrl: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasUserToken = remember { !TokenStore.getUserToken(context).isNullOrBlank() }

    var currentScreen by remember { mutableStateOf(AccountDialogScreen.PROFILES) }
    var previousScreen by remember { mutableStateOf(AccountDialogScreen.PROFILES) }

    // Requesters for dialog screens
    val defaultActionFocusRequester = remember { FocusRequester() }
    val warningConfirmFocusRequester = remember { FocusRequester() }
    val settingsFirstFocusRequester = remember { FocusRequester() }
    val helpBackFocusRequester = remember { FocusRequester() }

    val handleDismissWithFocus: () -> Unit = {
        onDismiss()
        TvFocusManager.restoreAccountFocus()
    }

    // Unified single-press back handler logic
    val handleBackPress: () -> Unit = {
        when (currentScreen) {
            AccountDialogScreen.WARNING -> {
                currentScreen = previousScreen
            }
            AccountDialogScreen.SETTINGS -> {
                currentScreen = AccountDialogScreen.PROFILES
            }
            AccountDialogScreen.HELP -> {
                currentScreen = AccountDialogScreen.PROFILES
            }
            AccountDialogScreen.PROFILES -> {
                handleDismissWithFocus()
            }
        }
    }

    // Fallback system BackHandler
    BackHandler(enabled = true) {
        handleBackPress()
    }

    // Auto-focus inside dialog on screen transition so focus never gets lost
    LaunchedEffect(currentScreen) {
        delay(60)
        when (currentScreen) {
            AccountDialogScreen.PROFILES -> runCatching { defaultActionFocusRequester.requestFocus() }
            AccountDialogScreen.SETTINGS -> runCatching { settingsFirstFocusRequester.requestFocus() }
            AccountDialogScreen.WARNING -> runCatching { warningConfirmFocusRequester.requestFocus() }
            AccountDialogScreen.HELP -> runCatching { helpBackFocusRequester.requestFocus() }
        }
    }

    val dialogCardWidth by animateDpAsState(
        targetValue = if (currentScreen == AccountDialogScreen.HELP) 580.dp else 460.dp,
        animationSpec = tween(180),
        label = "dialogCardWidth"
    )

    // Fullscreen dark overlay (centered dialog on the entire screen)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (currentScreen == AccountDialogScreen.PROFILES) {
                        handleDismissWithFocus()
                    } else {
                        currentScreen = AccountDialogScreen.PROFILES
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Main Dialog Card Container - intercepts Key.Back preview event for immediate 1-press response
        Box(
            modifier = Modifier
                .width(dialogCardWidth)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    ) {
                        handleBackPress()
                        true
                    } else false
                }
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E1E23),
                            Color(0xFF121215)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(22.dp)
                )
                .shadow(elevation = 32.dp, shape = RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = true,
                    onClick = {} // consume click so scrim click doesn't trigger
                )
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == AccountDialogScreen.WARNING || targetState == AccountDialogScreen.SETTINGS || targetState == AccountDialogScreen.HELP) {
                        (slideInHorizontally { it / 4 } + fadeIn(tween(160)))
                            .togetherWith(slideOutHorizontally { -it / 4 } + fadeOut(tween(130)))
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn(tween(160)))
                            .togetherWith(slideOutHorizontally { it / 4 } + fadeOut(tween(130)))
                    }
                },
                label = "AccountDialogNavigation"
            ) { screen ->
                when (screen) {
                    AccountDialogScreen.PROFILES -> {
                        ProfilesView(
                            accLogoUrl = accLogoUrl,
                            hasUserToken = hasUserToken,
                            defaultFocusRequester = defaultActionFocusRequester,
                            onOpenSettings = {
                                previousScreen = AccountDialogScreen.PROFILES
                                currentScreen = AccountDialogScreen.SETTINGS
                            },
                            onOpenHelp = {
                                previousScreen = AccountDialogScreen.PROFILES
                                currentScreen = AccountDialogScreen.HELP
                            },
                            onOpenWarning = {
                                previousScreen = AccountDialogScreen.PROFILES
                                currentScreen = AccountDialogScreen.WARNING
                            },
                            onBackPress = handleBackPress,
                            onDismiss = handleDismissWithFocus
                        )
                    }

                    AccountDialogScreen.SETTINGS -> {
                        AppSettingsView(
                            firstFocusRequester = settingsFirstFocusRequester,
                            onBack = {
                                currentScreen = AccountDialogScreen.PROFILES
                            },
                            onOpenWarning = {
                                previousScreen = AccountDialogScreen.SETTINGS
                                currentScreen = AccountDialogScreen.WARNING
                            },
                            onBackPress = handleBackPress,
                            onDismiss = handleDismissWithFocus
                        )
                    }

                    AccountDialogScreen.HELP -> {
                        HelpView(
                            backFocusRequester = helpBackFocusRequester,
                            onBack = {
                                currentScreen = AccountDialogScreen.PROFILES
                            },
                            onBackPress = handleBackPress
                        )
                    }

                    AccountDialogScreen.WARNING -> {
                        WarningConfirmationView(
                            confirmFocusRequester = warningConfirmFocusRequester,
                            onCancel = {
                                currentScreen = previousScreen
                            },
                            onConfirmClear = {
                                clearAllAppDataAndExit(context)
                            },
                            onBackPress = handleBackPress
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 1: Profiles View with strict D-pad focus trapping and immediate 1-press Back response.
 */
@Composable
private fun ProfilesView(
    accLogoUrl: String?,
    hasUserToken: Boolean,
    defaultFocusRequester: FocusRequester,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWarning: () -> Unit,
    onBackPress: () -> Unit,
    onDismiss: () -> Unit
) {
    val closeButtonFocusRequester = remember { FocusRequester() }
    val appSettingsRequester = defaultFocusRequester
    val accountRequester = remember { FocusRequester() }
    val helpRequester = remember { FocusRequester() }
    val clearDataRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Header Bar with Close Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Profiles",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )

            TvIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close Dialog",
                focusRequester = closeButtonFocusRequester,
                modifier = Modifier.align(Alignment.CenterEnd),
                onBackPress = onBackPress,
                onKeyDownCustom = { key ->
                    when (key) {
                        Key.DirectionDown -> {
                            appSettingsRequester.requestFocus()
                            true
                        }
                        Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true // trap focus inside dialog
                        else -> false
                    }
                },
                onClick = onDismiss
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Hero Profile Card Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF222228))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(18.dp))
                .padding(vertical = 18.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(5.dp)),
//                        .background(Color(0xFF2A2A30)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!accLogoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = accLogoUrl,
                            contentDescription = "Profile Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Profile Username
                Text(
                    text = if (hasUserToken) "Active User" else "Guest Account",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (hasUserToken) Color(0xFF4CAF50).copy(alpha = 0.16f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (hasUserToken) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (hasUserToken) Color(0xFF4CAF50) else Color(0xFFFFA000))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasUserToken) "Active Session" else "Guest Mode",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasUserToken) Color(0xFF81C784) else Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Structured Action Menu Rows with Focus Trapping
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App Settings (Top item)
            StructuredMenuItemRow(
                icon = Icons.Default.Settings,
                label = "App Settings",
                focusRequester = appSettingsRequester,
                onBackPress = onBackPress,
                onKeyDownCustom = { key ->
                    when (key) {
                        Key.DirectionUp -> {
                            closeButtonFocusRequester.requestFocus()
                            true
                        }
                        Key.DirectionLeft, Key.DirectionRight -> true // trap focus inside dialog
                        else -> false
                    }
                },
                onClick = onOpenSettings
            )


            // Help
            StructuredMenuItemRow(
                icon = Icons.Default.HelpOutline,
                label = "Help",
                showArrow = true,
                focusRequester = helpRequester,
                onBackPress = onBackPress,
                onKeyDownCustom = { key ->
                    when (key) {
                        Key.DirectionDown -> true // trap focus at bottom item
                        Key.DirectionLeft, Key.DirectionRight -> true // trap focus
                        else -> false
                    }
                },
                onClick = onOpenHelp
            )

        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. App Version Footer
        Text(
            text = "Version: ${AppSession.currentAppVersion()} (Android TV)",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Screen 2: Structured App Settings Sub-View with Focus Trapping.
 */
@Composable
private fun AppSettingsView(
    firstFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onOpenWarning: () -> Unit,
    onBackPress: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var cacheSize by remember { mutableLongStateOf(getCacheSizeBytes(context)) }
    val cacheSizeDisplay = remember(cacheSize) {
        String.format(Locale.US, "%.1f MB", cacheSize / (1024.0 * 1024.0))
    }

    val backButtonRequester = firstFocusRequester
    val closeButtonRequester = remember { FocusRequester() }
    val cleanCacheRequester = remember { FocusRequester() }
    val clearAllRequester = remember { FocusRequester() }
    val backToProfilesRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Back to Profile",
                    focusRequester = backButtonRequester,
                    onBackPress = onBackPress,
                    onKeyDownCustom = { key ->
                        when (key) {
                            Key.DirectionRight -> {
                                closeButtonRequester.requestFocus()
                                true
                            }
                            Key.DirectionDown -> {
                                cleanCacheRequester.requestFocus()
                                true
                            }
                            Key.DirectionUp, Key.DirectionLeft -> true
                            else -> false
                        }
                    },
                    onClick = onBack
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "App Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grouped Settings List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Storage & Clean Cache Row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222228))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Temporary Cache",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = cacheSizeDisplay,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }

                    // Clean Cache Button
                    AccountDialogButton(
                        label = "Clean",
                        icon = Icons.Default.CleaningServices,
                        isDanger = false,
                        focusRequester = cleanCacheRequester,
                        onBackPress = onBackPress,
                        onKeyDownCustom = { key ->
                            when (key) {
                                Key.DirectionUp -> {
                                    backButtonRequester.requestFocus()
                                    true
                                }
                                Key.DirectionDown -> {
                                    clearAllRequester.requestFocus()
                                    true
                                }
                                Key.DirectionLeft, Key.DirectionRight -> true
                                else -> false
                            }
                        },
                        onClick = {
                            try {
                                context.cacheDir.deleteRecursively()
                                context.codeCacheDir.deleteRecursively()
                                cacheSize = getCacheSizeBytes(context)
                                Toast.makeText(context, "Cache cleaned!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }


            // Clear All App Data (Danger Item)
            StructuredMenuItemRow(
                icon = Icons.Default.DeleteForever,
                label = "Clear All App Data & Exit",
                isDanger = true,
                focusRequester = clearAllRequester,
                onBackPress = onBackPress,
                onKeyDownCustom = { key ->
                    when (key) {
                        Key.DirectionUp -> {
                            cleanCacheRequester.requestFocus()
                            true
                        }
                        Key.DirectionDown -> {
                            backToProfilesRequester.requestFocus()
                            true
                        }
                        Key.DirectionLeft, Key.DirectionRight -> true
                        else -> false
                    }
                },
                onClick = onOpenWarning
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Back Button
        AccountDialogButton(
            label = "Back to Profiles",
            icon = Icons.Default.ArrowBack,
            isDanger = false,
            focusRequester = backToProfilesRequester,
            modifier = Modifier.fillMaxWidth(),
            onBackPress = onBackPress,
            onKeyDownCustom = { key ->
                when (key) {
                    Key.DirectionUp -> {
                        clearAllRequester.requestFocus()
                        true
                    }
                    Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                    else -> false
                }
            },
            onClick = onBack
        )
    }
}

/**
 * Screen 3: Structured Danger Warning Confirmation Layer with locked D-pad navigation.
 */
@Composable
private fun WarningConfirmationView(
    confirmFocusRequester: FocusRequester,
    onCancel: () -> Unit,
    onConfirmClear: () -> Unit,
    onBackPress: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Red Warning Emblem
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFFE50914).copy(alpha = 0.16f))
                .border(1.5.dp, Color(0xFFE50914), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFE50914),
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Clear All Data & Exit?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Are you sure you want to clear app data and exit app completely?",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFF5252),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This will permanently remove your watchlist, resume playback, and continue watching history. The app will close immediately.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Danger Clear & Exit Button
            AccountDialogButton(
                label = "Clear & Exit",
                icon = Icons.Default.PowerSettingsNew,
                isDanger = true,
                focusRequester = confirmFocusRequester,
                modifier = Modifier.weight(1f),
                onBackPress = onBackPress,
                onKeyDownCustom = { key ->
                    when (key) {
                        Key.DirectionRight -> {
                            cancelFocusRequester.requestFocus()
                            true
                        }
                        Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> true
                        else -> false
                    }
                },
                onClick = onConfirmClear
            )

            // Cancel Button
            AccountDialogButton(
                label = "Cancel",
                icon = Icons.Default.Close,
                isDanger = false,
                focusRequester = cancelFocusRequester,
                modifier = Modifier.weight(1f),
                onBackPress = onBackPress,
                onKeyDownCustom = { key ->
                    when (key) {
                        Key.DirectionLeft -> {
                            confirmFocusRequester.requestFocus()
                            true
                        }
                        Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
                        else -> false
                    }
                },
                onClick = onCancel
            )
        }
    }
}

/**
 * Screen 4: Help View displaying the large help image with back button to main menu.
 */
@Composable
private fun HelpView(
    backFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onBackPress: () -> Unit
) {
    val helpImageUrl = "https://dimg04.tripcdn.com/images/0a13m12000tn3gjwxFE31.jpg"
    val headerBackRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvIconButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back to Main Menu",
                focusRequester = headerBackRequester,
                onBackPress = onBackPress,
                onKeyDownCustom = { key ->
                    when (key) {
                        Key.DirectionDown -> {
                            backFocusRequester.requestFocus()
                            true
                        }
                        Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                        else -> false
                    }
                },
                onClick = onBack
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Help",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Large Help Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B1B20))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

            AsyncImage(
                model = helpImageUrl,
                contentDescription = "Help Image",
                contentScale = ContentScale.Fit,
                onState = { state ->
                    imageState = state
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )

            when (imageState) {
                is AsyncImagePainter.State.Loading, is AsyncImagePainter.State.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(42.dp),
                            color = Color(0xFFE50914),
                            strokeWidth = 3.5.dp,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Failed to load image",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
                is AsyncImagePainter.State.Success -> {
                    // Image loaded successfully
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Back Button to Main Menu
        AccountDialogButton(
            label = "Back to Main Menu",
            icon = Icons.Default.ArrowBack,
            isDanger = false,
            focusRequester = backFocusRequester,
            modifier = Modifier.fillMaxWidth(),
            onBackPress = onBackPress,
            onKeyDownCustom = { key ->
                when (key) {
                    Key.DirectionUp -> {
                        headerBackRequester.requestFocus()
                        true
                    }
                    Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
                    else -> false
                }
            },
            onClick = onBack
        )
    }
}

/**
 * Structured, clean, single-line menu item row with D-pad trap and immediate 1-press Back support.
 */
@Composable
private fun StructuredMenuItemRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    showArrow: Boolean = true,
    isDanger: Boolean = false,
    focusRequester: FocusRequester? = null,
    onBackPress: (() -> Unit)? = null,
    onKeyDownCustom: ((Key) -> Boolean)? = null,
    onClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1f,
        animationSpec = tween(110),
        label = "menuItemScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isDanger -> Color(0xFFE50914).copy(alpha = 0.12f)
            else -> Color(0xFF222228)
        },
        animationSpec = tween(110),
        label = "menuItemBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isDanger -> Color(0xFFE50914).copy(alpha = 0.35f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(110),
        label = "menuItemBorder"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            isDanger -> Color(0xFFFF5252)
            else -> Color.White
        },
        animationSpec = tween(110),
        label = "menuItemText"
    )

    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (!subtitle.isNullOrBlank()) 56.dp else 50.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(backgroundColor)
            .border(width = if (isFocused) 2.dp else 1.dp, color = borderColor, shape = shape)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        if (onBackPress != null) {
                            onBackPress()
                            return@onPreviewKeyEvent true
                        }
                    }
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        onClick?.invoke()
                        return@onPreviewKeyEvent true
                    }
                    if (onKeyDownCustom != null && onKeyDownCustom(event.key)) {
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .focusable()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isFocused) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showArrow) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = if (isFocused) 1f else 0.45f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Focusable Icon button for Top Header (e.g. Back, Close) with D-pad trap and immediate Back support.
 */
@Composable
private fun TvIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onBackPress: (() -> Unit)? = null,
    onKeyDownCustom: ((Key) -> Boolean)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(110),
        label = "iconBtnScale"
    )

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(32.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (isFocused) Color.White else Color.Transparent, CircleShape)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        if (onBackPress != null) {
                            onBackPress()
                            return@onPreviewKeyEvent true
                        }
                    }
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        onClick()
                        return@onPreviewKeyEvent true
                    }
                    if (onKeyDownCustom != null && onKeyDownCustom(event.key)) {
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(17.dp)
        )
    }
}

/**
 * Focusable Button with TV remote D-pad support and immediate 1-press Back response.
 */
@Composable
private fun AccountDialogButton(
    label: String,
    icon: ImageVector,
    isDanger: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onBackPress: (() -> Unit)? = null,
    onKeyDownCustom: ((Key) -> Boolean)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(110),
        label = "btnScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isDanger -> Color(0xFFE50914).copy(alpha = 0.22f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(110),
        label = "btnBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isDanger -> Color(0xFFE50914)
            else -> Color.White.copy(alpha = 0.2f)
        },
        animationSpec = tween(110),
        label = "btnBorder"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            isDanger -> Color(0xFFFF4D4D)
            else -> Color.White
        },
        animationSpec = tween(110),
        label = "btnContent"
    )

    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        if (onBackPress != null) {
                            onBackPress()
                            return@onPreviewKeyEvent true
                        }
                    }
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        onClick()
                        return@onPreviewKeyEvent true
                    }
                    if (onKeyDownCustom != null && onKeyDownCustom(event.key)) {
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            color = contentColor
        )
    }
}

/**
 * Calculates local cache size in bytes.
 */
private fun getCacheSizeBytes(context: Context): Long {
    return try {
        fun dirSize(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            var total = 0L
            dir.listFiles()?.forEach { file ->
                total += if (file.isDirectory) dirSize(file) else file.length()
            }
            return total
        }
        dirSize(context.cacheDir) + dirSize(context.codeCacheDir)
    } catch (e: Exception) {
        0L
    }
}

/**
 * Thoroughly wipes all local caches, SharedPreferences, Watchlist,
 * Playback progress, and credentials, then completely exits the application.
 */
private fun clearAllAppDataAndExit(context: Context) {
    try {
        // 1. Clear SharedPreferences
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        if (sharedPrefsDir.exists()) {
            sharedPrefsDir.listFiles()?.forEach { file ->
                val prefName = file.nameWithoutExtension
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
            }
            sharedPrefsDir.deleteRecursively()
        }

        // 2. Explicitly wipe individual known store files
        TokenStore.clearToken(context)
        context.getSharedPreferences("netmirror_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("netmirror_watchlist_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("netmirror_playback_progress", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("netmirror_recentplay_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("netmirror_player_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        // 3. Reset in-memory session
        AppSession.setOtt("nf")

        // 4. Clear cache directories and files
        context.cacheDir.deleteRecursively()
        context.codeCacheDir.deleteRecursively()
        context.filesDir.deleteRecursively()
        val dbDir = File(context.applicationInfo.dataDir, "databases")
        if (dbDir.exists()) {
            dbDir.deleteRecursively()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        // 5. Terminate and exit process completely
        (context as? Activity)?.finishAffinity()
        exitProcess(0)
    }
}