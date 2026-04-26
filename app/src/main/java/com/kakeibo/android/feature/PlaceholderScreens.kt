package com.kakeibo.android.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kakeibo.android.R

@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title)
            Text(text = stringResource(R.string.placeholder_screen, title))
        }
    }
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(stringResource(R.string.nav_dashboard), modifier.padding(16.dp))

@Composable
fun TransactionListScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(stringResource(R.string.nav_transactions), modifier.padding(16.dp))

@Composable
fun TransactionFormScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(stringResource(R.string.nav_add), modifier.padding(16.dp))

@Composable
fun AnalysisScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(stringResource(R.string.nav_analysis), modifier.padding(16.dp))

@Composable
fun MoreScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(stringResource(R.string.nav_more), modifier.padding(16.dp))
