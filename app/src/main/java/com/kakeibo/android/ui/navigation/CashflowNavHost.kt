package com.kakeibo.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kakeibo.android.feature.AnalysisScreen
import com.kakeibo.android.feature.MoreScreen
import com.kakeibo.android.feature.dashboard.DashboardScreen
import com.kakeibo.android.feature.transactions.TransactionFormScreen
import com.kakeibo.android.feature.transactions.TransactionListScreen

/** Route for editing a transaction; [id] is the transaction UUID. */
fun transactionEditRoute(id: String): String = "transactions/$id/edit"

/** Route for the new-transaction form, optionally prefilled from a template. */
fun transactionNewRoute(templateId: String? = null): String =
    if (templateId == null) "transactions/new" else "transactions/new?templateId=$templateId"

@Composable
fun CashflowNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Dashboard.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.Dashboard.route) {
            DashboardScreen(
                onAddNew = { navController.navigate(transactionNewRoute()) },
                onUseTemplate = { id -> navController.navigate(transactionNewRoute(id)) },
                onOpenTransactions = { navController.navigate(TopLevelDestination.Transactions.route) },
                onEditTransaction = { id -> navController.navigate(transactionEditRoute(id)) },
            )
        }
        composable(TopLevelDestination.Transactions.route) {
            TransactionListScreen(
                onAddNew = { navController.navigate(transactionNewRoute()) },
                onEdit = { id -> navController.navigate(transactionEditRoute(id)) },
            )
        }
        // New-transaction form. The Add tab navigates here without a templateId; the dashboard/list
        // quick-add passes one to prefill. Optional query arg keeps the base "transactions/new" route.
        composable(
            route = "transactions/new?templateId={templateId}",
            arguments = listOf(
                navArgument("templateId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) {
            TransactionFormScreen(onClose = { navController.popBackStack() })
        }
        composable(
            route = "transactions/{id}/edit",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            TransactionFormScreen(onClose = { navController.popBackStack() })
        }
        composable(TopLevelDestination.Analysis.route) { AnalysisScreen() }
        composable(TopLevelDestination.More.route) { MoreScreen() }
    }
}
