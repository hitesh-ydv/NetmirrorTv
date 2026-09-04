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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.netmirror.netmirrortv.ui.components.AccountDialog
import com.netmirror.netmirrortv.ui.components.AppToast
import com.netmirror.netmirrortv.ui.components.NavbarViewModel
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    appNavController: NavHostController
) {

    val navController = rememberNavController()

    val homeViewModel: HomeViewModel = viewModel()
    val homeError by homeViewModel.error.collectAsState()

    val navbarViewModel: NavbarViewModel = viewModel()
    val navbar by navbarViewModel.navbar.collectAsState()

    var showAccountDialog by remember { mutableStateOf(false) }
    var wasAccountDialogOpened by remember { mutableStateOf(false) }

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
        // IMPORTANT
        selectedIndex = index
        TvFocusManager.lastFocusedContentRequester = null
        TvFocusManager.lastFocusedHomeCardId = null
        TvFocusManager.lastFocusedHomeRowPosition = null
        TvFocusManager.isCarouselLastFocused = false
        TvFocusManager.lastFocusedSearchCardId = null
        TvFocusManager.currentScreenContentRequester = null
        TvFocusManager.firstCardRequester = null
        TvFocusManager.rowFocusRequesters.clear()

        when (index) {
            // Home
            0 -> {
                homeViewModel.loadHome("all")

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
                homeViewModel.loadHome("show")

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
                homeViewModel.loadHome("movie")

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
    }
}