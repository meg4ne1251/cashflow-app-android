package com.kakeibo.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard("dashboard", com.kakeibo.android.R.string.nav_dashboard, Icons.Outlined.Dashboard),
    Transactions("transactions", com.kakeibo.android.R.string.nav_transactions, Icons.Outlined.AccessTime),
    Add("transactions/new", com.kakeibo.android.R.string.nav_add, Icons.Outlined.Add),
    Analysis("analysis", com.kakeibo.android.R.string.nav_analysis, Icons.Outlined.BarChart),
    More("more", com.kakeibo.android.R.string.nav_more, Icons.Outlined.MoreHoriz),
}
