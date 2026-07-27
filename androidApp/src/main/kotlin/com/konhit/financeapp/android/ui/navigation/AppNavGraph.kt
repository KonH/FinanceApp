package com.konhit.financeapp.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.konhit.financeapp.android.ui.screens.account.AccountScreen
import com.konhit.financeapp.android.ui.screens.accounts.AccountsScreen
import com.konhit.financeapp.android.ui.screens.categories.CategoriesScreen
import com.konhit.financeapp.android.ui.screens.currencies.CurrenciesScreen
import com.konhit.financeapp.android.ui.screens.filter.FilterScreen
import com.konhit.financeapp.android.ui.screens.firstlaunch.FirstLaunchScreen
import com.konhit.financeapp.android.ui.screens.main.MainScreen
import com.konhit.financeapp.android.ui.screens.scheduled.ScheduledEditScreen
import com.konhit.financeapp.android.ui.screens.scheduled.ScheduledListScreen
import com.konhit.financeapp.android.ui.screens.settings.SettingsScreen
import com.konhit.financeapp.android.ui.screens.transaction.TransactionScreen

@Composable
fun AppNavGraph(
    startDestination: String,
    onDatabaseReady: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.FIRST_LAUNCH) {
            FirstLaunchScreen(
                onFileReady = {
                    onDatabaseReady()
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.FIRST_LAUNCH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            LaunchedEffect(Unit) { onDatabaseReady() }
            MainScreen(
                onAccountClick = { id -> navController.navigate(Routes.account(id)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onFilterClick = { navController.navigate(Routes.filter()) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAccountsClick = { navController.navigate(Routes.ACCOUNTS) },
                onCategoriesClick = { navController.navigate(Routes.CATEGORIES) },
                onCurrenciesClick = { navController.navigate(Routes.CURRENCIES) },
                onScheduledClick = { navController.navigate(Routes.SCHEDULED) },
                onCloseDatabase = {
                    navController.navigate(Routes.FIRST_LAUNCH) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ACCOUNTS) {
            AccountsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CATEGORIES) {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CURRENCIES) {
            CurrenciesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SCHEDULED) {
            ScheduledListScreen(
                onAdd = { navController.navigate(Routes.scheduledEdit()) },
                onEdit = { bdId -> navController.navigate(Routes.scheduledEdit(bdId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.SCHEDULED_EDIT,
            arguments = listOf(navArgument("bdId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStack ->
            val bdId = backStack.arguments?.getLong("bdId")?.takeIf { it != -1L }
            ScheduledEditScreen(
                editBdId = bdId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
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
                onFilterClick = { navController.navigate(Routes.filter(accountId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.FILTER,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType; defaultValue = -1L })
        ) {
            FilterScreen(onBack = { navController.popBackStack() })
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
