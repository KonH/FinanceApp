package com.konhit.financeapp.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.konhit.financeapp.android.ui.screens.account.AccountScreen
import com.konhit.financeapp.android.ui.screens.firstlaunch.FirstLaunchScreen
import com.konhit.financeapp.android.ui.screens.main.MainScreen
import com.konhit.financeapp.android.ui.screens.settings.SettingsScreen
import com.konhit.financeapp.android.ui.screens.transaction.TransactionScreen

@Composable
fun AppNavGraph(startDestination: String) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.FIRST_LAUNCH) {
            FirstLaunchScreen(
                onFileReady = { navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.FIRST_LAUNCH) { inclusive = true }
                }}
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onAccountClick = { id -> navController.navigate(Routes.account(id)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.ACCOUNT,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStack ->
            val accountId = backStack.arguments?.getLong("accountId") ?: return@composable
            AccountScreen(
                accountId = accountId,
                onAddTransaction = { navController.navigate(Routes.transactionAdd(accountId)) },
                onEditTransaction = { transId -> navController.navigate(Routes.transactionEdit(transId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.TRANSACTION,
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("transId")   { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStack ->
            val accountId = backStack.arguments?.getLong("accountId") ?: -1L
            val transId   = backStack.arguments?.getLong("transId")   ?: -1L
            TransactionScreen(
                initialAccountId = accountId.takeIf { it != -1L },
                editTransId      = transId.takeIf   { it != -1L },
                onSaved = { navController.popBackStack() },
                onBack  = { navController.popBackStack() }
            )
        }
    }
}
