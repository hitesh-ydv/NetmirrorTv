package com.netmirror.netmirrortv.ui.main

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netmirror.netmirrortv.navigation.Routes
import com.netmirror.netmirrortv.ui.components.AppModalNavigationDrawer
import com.netmirror.netmirrortv.ui.home.HomeScreen
import com.netmirror.netmirrortv.ui.home.HomeViewModel
import com.netmirror.netmirrortv.ui.ott.OttScreen
import com.netmirror.netmirrortv.ui.search.SearchScreen
import com.netmirror.netmirrortv.utils.TvFocusManager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.netmirror.netmirrortv.datastore.AppPreferences
import com.netmirror.netmirrortv.model.UpdateMessageResponse
import com.netmirror.netmirrortv.network.ApiClient
import com.netmirror.netmirrortv.network.BaseUrlProvider
import com.netmirror.netmirrortv.ui.components.AccountDialog
import com.netmirror.netmirrortv.ui.components.AppToast
import com.netmirror.netmirrortv.ui.components.NavbarViewModel
import com.netmirror.netmirrortv.ui.components.UpdateDialog
import com.netmirror.netmirrortv.util.TokenStore
import com.netmirror.netmirrortv.utils.AppSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    appNavController: NavHostController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isRedirectingToOtp by remember { mutableStateOf(false) }

    fun checkTokenAndNavigate() {
        if (isRedirectingToOtp) return
        val hasToken = !TokenStore.getUserToken(context).isNullOrBlank()
        if (!hasToken) {
            isRedirectingToOtp = true
            val baseUrl = BaseUrlProvider.getBaseUrl()
            appNavController.navigate(Routes.otp(baseUrl)) {
                popUpTo(Routes.MAIN) { inclusive = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkTokenAndNavigate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkTokenAndNavigate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isRedirectingToOtp) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    val homeViewModel: HomeViewModel = viewModel()
    val homeError by homeViewModel.error.collectAsState()

    val navbarViewModel: NavbarViewModel = viewModel()
    val navbar by navbarViewModel.navbar.collectAsState()

    var showAccountDialog by remember { mutableStateOf(false) }
    var wasAccountDialogOpened by remember { mutableStateOf(false) }

    var updateDialogData by remember { mutableStateOf<UpdateMessageResponse?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    suspend fun checkUpdate() {
        if (AppSession.hasShownUpdateDialog) return

        val prefs = AppPreferences(context)
        val apiVer = AppSession.currentApiVersion().ifBlank { prefs.getApiVersion() }.trim()
        val appVer = AppSession.currentAppVersion().ifBlank { prefs.getAppVersion() }.trim()

        if (apiVer.isNotBlank() && appVer.isNotBlank() && apiVer != appVer) {
            try {
                val baseUrl = BaseUrlProvider.getBaseUrl().ifBlank { prefs.getBaseUrl() }
                if (baseUrl.isNotBlank()) {
                    val response = ApiClient.create(baseUrl).getUpdateMessage()
                    if (response.isSuccessful && response.body() != null) {
                        AppSession.cachedUpdateData = response.body()
                        updateDialogData = response.body()
                        showUpdateDialog = true
                    } else if (AppSession.cachedUpdateData != null) {
                        updateDialogData = AppSession.cachedUpdateData
                        showUpdateDialog = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (AppSession.cachedUpdateData != null) {
                    updateDialogData = AppSession.cachedUpdateData
                    showUpdateDialog = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkUpdate()
    }

    LaunchedEffect(Unit) {
        AppSession.updateCheckTrigger.collect {
            checkUpdate()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

    var selectedIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == Routes.HOME && selectedIndex !in 0..2) {
            selectedIndex = 0
        }
    }

    val onSelectTab: (Int) -> Unit = { index ->
        selectedIndex = index

        when (index) {
            // Home
            0 -> {
                if (homeViewModel.page.value != "all") {
                    homeViewModel.loadHome("all")
                    TvFocusManager.lastFocusedContentRequester = null
                    TvFocusManager.lastFocusedHomeCardId = null
                    TvFocusManager.lastFocusedHomeRowPosition = null
                    TvFocusManager.isCarouselLastFocused = false
                }

                if (currentRoute != Routes.HOME) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

            // TV Shows
            1 -> {
                if (homeViewModel.page.value != "show") {
                    homeViewModel.loadHome("show")
                    TvFocusManager.lastFocusedContentRequester = null
                    TvFocusManager.lastFocusedHomeCardId = null
                    TvFocusManager.lastFocusedHomeRowPosition = null
                    TvFocusManager.isCarouselLastFocused = false
                }

                if (currentRoute != Routes.HOME) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

            // Movies
            2 -> {
                if (homeViewModel.page.value != "movie") {
                    homeViewModel.loadHome("movie")
                    TvFocusManager.lastFocusedContentRequester = null
                    TvFocusManager.lastFocusedHomeCardId = null
                    TvFocusManager.lastFocusedHomeRowPosition = null
                    TvFocusManager.isCarouselLastFocused = false
                }

                if (currentRoute != Routes.HOME) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

            // Search
            3 -> {
                TvFocusManager.lastFocusedSearchCardId = null
                if (currentRoute != Routes.SEARCH) {
                    navController.navigate(Routes.SEARCH) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

            // OTT
            4 -> {
                if (currentRoute != Routes.OTT) {
                    navController.navigate(Routes.OTT) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppModalNavigationDrawer(
            selectedIndex = selectedIndex,
            onItemSelected = onSelectTab,
            onAccountClick = {
                showAccountDialog = true
            },
            isAccountDialogOpen = showAccountDialog
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        appNavController = appNavController,
                    )
                }

                composable(Routes.SEARCH) {
                    SearchScreen(appNavController = appNavController)
                }

                composable(Routes.OTT) {
                    OttScreen(
                        onNavigateToHome = {
                            onSelectTab(0)
                        }
                    )
                }
            }
        }

        if (homeError != null) {
            AppToast(
                title = "Network Error",
                message = "Main Page Loading Fail, Retry after some times.",
                durationMs = 10000L,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )
        }

        LaunchedEffect(showAccountDialog) {
            if (showAccountDialog) {
                wasAccountDialogOpened = true
            } else if (wasAccountDialogOpened) {
                repeat(8) { attempt ->
                    val restored = TvFocusManager.restoreAccountFocus()
                    if (restored) return@LaunchedEffect
                    delay(35L * (attempt + 1))
                }
            }
        }

        AnimatedVisibility(
            visible = showAccountDialog,
            enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(240, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                    scaleOut(targetScale = 0.92f, animationSpec = tween(180, easing = FastOutSlowInEasing))
        ) {
            AccountDialog(
                accLogoUrl = navbar?.acc_logo,
                onDismiss = {
                    showAccountDialog = false
                    TvFocusManager.restoreAccountFocus()
                }
            )
        }

        val currentUpdateData = updateDialogData
        AnimatedVisibility(
            visible = showUpdateDialog && currentUpdateData != null,
            enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(240, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                    scaleOut(targetScale = 0.92f, animationSpec = tween(180, easing = FastOutSlowInEasing))
        ) {
            if (currentUpdateData != null) {
                UpdateDialog(
                    updateData = currentUpdateData,
                    onDismiss = {
                        AppSession.hasShownUpdateDialog = true
                        showUpdateDialog = false
                        coroutineScope.launch {
                            repeat(12) { attempt ->
                                val success = TvFocusManager.focusCarouselDirectly()
                                if (success) return@launch
                                delay(35L * (attempt + 1))
                            }
                        }
                    }
                )
            }
        }
    }
}