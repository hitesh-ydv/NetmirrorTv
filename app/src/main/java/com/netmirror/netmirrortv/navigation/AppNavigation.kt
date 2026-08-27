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
    const val PLAYER = "player/{id}?mainId={mainId}&type={type}"

    // apiUrl is the working mirror base URL resolved by the splash server
    // check — URL-encoded going in via otp(), decoded back out in
    // AppNavigation's composable() block.
    const val OTP = "otp/{apiUrl}"

    fun details(id: String) = "details/$id"
    fun player(id: String, mainId: String = "", type: String = ""): String {
        val queryParams = mutableListOf<String>()
        if (mainId.isNotBlank()) queryParams.add("mainId=${Uri.encode(mainId)}")
        if (type.isNotBlank()) queryParams.add("type=${Uri.encode(type)}")
        return if (queryParams.isNotEmpty()) {
            "player/$id?${queryParams.joinToString("&")}"
        } else {
            "player/$id"
        }
    }
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
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            DetailsScreen(navController, id)
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                },
                navArgument("mainId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val mainId = backStackEntry.arguments?.getString("mainId") ?: ""
            val type = backStackEntry.arguments?.getString("type") ?: ""
            PlayerScreen(id = id, mainId = mainId, type = type)
        }
    }
}