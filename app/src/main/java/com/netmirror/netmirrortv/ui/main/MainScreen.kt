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

@Composable
fun MainScreen(
    appNavController: NavHostController
) {

    val navController = rememberNavController()

    val homeViewModel: HomeViewModel = viewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

    var selectedIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    AppModalNavigationDrawer(

        selectedIndex = selectedIndex,

        onItemSelected = { index ->

            // IMPORTANT
            selectedIndex = index
            TvFocusManager.lastFocusedContentRequester = null

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
                SearchScreen()
            }

            composable(Routes.OTT) {
                OttScreen()
            }
        }
    }
}