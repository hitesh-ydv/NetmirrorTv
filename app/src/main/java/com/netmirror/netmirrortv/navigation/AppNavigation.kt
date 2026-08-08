package com.netmirror.netmirrortv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.netmirror.netmirrortv.ui.details.DetailsScreen
import com.netmirror.netmirrortv.ui.main.MainScreen
import com.netmirror.netmirrortv.ui.otp.OtpScreen
import com.netmirror.netmirrortv.ui.splash.SplashScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.netmirror.netmirrortv.ui.player.PlayerScreen

object Routes {
    const val SPLASH = "splash"
    const val MAIN = "main"

    const val HOME = "home"
    const val TV_SHOWS = "tv_shows"
    const val MOVIES = "movies"
    const val SEARCH = "search"
    const val OTT = "ott"

    const val DETAILS = "details/{id}"
    const val PLAYER = "player/{id}"

    // apiUrl is the working mirror base URL resolved by the splash server
    // check — URL-encoded going in via otp(), decoded back out in
    // AppNavigation's composable() block.
    const val OTP = "otp/{apiUrl}"

    fun details(id: Int) = "details/$id"
    fun player(id: Int) = "player/$id"
    fun otp(apiUrl: String) = "otp/${Uri.encode(apiUrl)}"
}

@Composable
fun AppNavigation(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {

        composable(Routes.SPLASH) {
            SplashScreen(navController)
        }

        composable(Routes.MAIN) {
            MainScreen(navController)
        }

        composable(
            route = Routes.OTP,
            arguments = listOf(
                navArgument("apiUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val rawUrl = backStackEntry.arguments?.getString("apiUrl") ?: ""
            val apiUrl = if (rawUrl.isBlank() || rawUrl == "{apiUrl}") "" else Uri.decode(rawUrl)
            OtpScreen(navController, apiUrl)
        }

        composable(
            route = Routes.DETAILS,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            DetailsScreen(navController, id)
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            // For testing purposes, we ignore the passed id and use fixed test data.
            // In production, you would fetch the stream details using the id.
            val id = backStackEntry.arguments?.getInt("id") ?: 0

            PlayerScreen(
            )
        }
    }
}