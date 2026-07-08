package com.kakeibo.android.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kakeibo.android.ui.navigation.CashflowNavHost
import com.kakeibo.android.ui.navigation.TopLevelDestination

@Composable
fun AppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Strip optional query args (e.g. the Add form's "transactions/new?templateId=...") so the
    // current route can be matched against a tab's plain route.
    val currentRoute = backStackEntry?.destination?.route?.substringBefore('?')

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = backStackEntry?.destination?.hierarchy?.any {
                        it.route?.substringBefore('?') == destination.route
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        CashflowNavHost(
            navController = navController,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding),
        )
    }
}
