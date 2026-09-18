package com.treg.llmpersonalization.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.treg.llmpersonalization.AppState
import com.treg.llmpersonalization.ui.screens.AddUserScreen
import com.treg.llmpersonalization.ui.screens.BeliefsScreen
import com.treg.llmpersonalization.ui.screens.DevScreen
import com.treg.llmpersonalization.ui.screens.MainScreen
import com.treg.llmpersonalization.ui.screens.SettingsScreen
import com.treg.llmpersonalization.ui.screens.SplashScreen
import com.treg.llmpersonalization.ui.screens.UserPickerScreen

object Routes {
    const val SPLASH = "splash"
    const val USER_PICKER = "user_picker"
    const val ADD_USER = "add_user"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val BELIEFS = "beliefs"
    const val DEV = "dev"
}

@Composable
fun AppNavHost(appState: AppState) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(appState) { hasUsers ->
                val dest = if (hasUsers) Routes.USER_PICKER else Routes.ADD_USER
                nav.navigate(dest) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }

        composable(Routes.USER_PICKER) {
            UserPickerScreen(
                appState = appState,
                onUserSelected = {
                    nav.navigate(Routes.MAIN) { popUpTo(0) }
                },
                onAddUser = { nav.navigate(Routes.ADD_USER) }
            )
        }

        composable(Routes.ADD_USER) {
            AddUserScreen(
                appState = appState,
                onUserCreated = {
                    nav.navigate(Routes.MAIN) { popUpTo(0) }
                },
                onCancel = { nav.popBackStack() }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                appState = appState,
                onOpenSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                appState = appState,
                onBack = { nav.popBackStack() },
                onOpenDev = { nav.navigate(Routes.DEV) },
                onOpenBeliefs = { nav.navigate(Routes.BELIEFS) },
                onSwitchUser = {
                    appState.clearUser()
                    nav.navigate(Routes.USER_PICKER) { popUpTo(0) }
                }
            )
        }

        composable(Routes.DEV) {
            DevScreen(appState, onBack = { nav.popBackStack() })
        }

        composable(Routes.BELIEFS) {
            BeliefsScreen(appState, onBack = { nav.popBackStack() })
        }
    }
}