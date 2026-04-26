package com.kakeibo.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kakeibo.android.feature.AnalysisScreen
import com.kakeibo.android.feature.DashboardScreen
import com.kakeibo.android.feature.MoreScreen
import com.kakeibo.android.feature.TransactionFormScreen
import com.kakeibo.android.feature.TransactionListScreen

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
        composable(TopLevelDestination.Dashboard.route) { DashboardScreen() }
        composable(TopLevelDestination.Transactions.route) { TransactionListScreen() }
        composable(TopLevelDestination.Add.route) { TransactionFormScreen() }
        composable(TopLevelDestination.Analysis.route) { AnalysisScreen() }
        composable(TopLevelDestination.More.route) { MoreScreen() }
    }
}
