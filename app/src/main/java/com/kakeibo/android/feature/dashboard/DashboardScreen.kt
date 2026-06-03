package com.kakeibo.android.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kakeibo.android.R
import com.kakeibo.android.core.data.dashboard.BudgetConsumption
import com.kakeibo.android.core.data.dashboard.DashboardUiData
import com.kakeibo.android.core.data.dashboard.QuickTemplate
import com.kakeibo.android.core.database.dao.TransactionListItem
import com.kakeibo.android.core.util.formatYen
import com.kakeibo.android.core.util.formatYenSigned
import com.kakeibo.android.core.util.formatYenSignedSum
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    onAddNew: () -> Unit,
    onUseTemplate: (String) -> Unit,
    onOpenTransactions: () -> Unit,
    onEditTransaction: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val data by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(data)
        StatRow(data)
        BudgetCard(data.budgetConsumption)
        AccountsCard(data)
        RecentCard(data.recent, onOpenTransactions, onEditTransaction)
        QuickAddCard(data.quickTemplates, onUseTemplate, onAddNew)
    }
}

@Composable
private fun HeroCard(data: DashboardUiData) {
    val today = LocalDate.now()
    val dayProgress = today.dayOfMonth.toFloat() / today.lengthOfMonth()
    val hasBudget = data.budgetTotal > 0
    val spentPct = if (hasBudget) data.monthExpense.toFloat() / data.budgetTotal else 0f
    val diff = (data.budgetTotal * dayProgress).toLong() - data.monthExpense

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dash_hero_label), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatYen(data.monthExpense), style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                if (hasBudget) {
                    Text("/ ${stringResource(R.string.dash_budget_title).take(2)} ${formatYen(data.budgetTotal)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hasBudget) {
                LinearProgressIndicator(
                    progress = { spentPct.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (diff >= 0) {
                        "${stringResource(R.string.dash_budget_pace_ok)} ${formatYen(diff)}"
                    } else {
                        "${stringResource(R.string.dash_budget_pace_over)} ${formatYen(-diff)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            } else {
                Text(stringResource(R.string.dash_budget_unset), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Sparkline(data.spark, Modifier.fillMaxWidth().height(48.dp))
        }
    }
}

@Composable
private fun StatRow(data: DashboardUiData) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(stringResource(R.string.dash_income), "+" + formatYen(data.monthIncome),
            MaterialTheme.colorScheme.primary, data.incomeChangeRate, Modifier.weight(1f))
        StatCard(stringResource(R.string.dash_expense), "−" + formatYen(data.monthExpense),
            MaterialTheme.colorScheme.error, data.expenseChangeRate, Modifier.weight(1f))
        StatCard(stringResource(R.string.dash_balance), formatYenSignedSum(data.balance),
            if (data.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            null, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, amount: String, color: Color, changeRate: Double?, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = color)
            if (changeRate != null) {
                val pct = (changeRate * 100).roundToInt()
                Text("${stringResource(R.string.dash_mom)} ${if (pct >= 0) "+" else ""}$pct%",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BudgetCard(items: List<BudgetConsumption>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.dash_budget_title), style = MaterialTheme.typography.titleSmall)
            if (items.isEmpty()) {
                Text(stringResource(R.string.dash_budget_empty), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                items.take(5).forEach { b ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.categoryName, style = MaterialTheme.typography.bodyMedium)
                            Text("${formatYen(b.spent)} / ${formatYen(b.budget)}",
                                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { b.rate.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (b.rate >= 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountsCard(data: DashboardUiData) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dash_accounts_title), style = MaterialTheme.typography.titleSmall)
            if (data.accounts.isEmpty()) {
                Text(stringResource(R.string.dash_accounts_empty), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                data.accounts.forEach { a ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(a.name, style = MaterialTheme.typography.bodyMedium)
                        Text(formatYenSignedSum(a.balance), style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = if (a.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(
    recent: List<TransactionListItem>,
    onOpenTransactions: () -> Unit,
    onEditTransaction: (String) -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dash_recent_title), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onOpenTransactions) { Text(stringResource(R.string.dash_see_all)) }
            }
            if (recent.isEmpty()) {
                Text(stringResource(R.string.dash_recent_empty), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                recent.forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onEditTransaction(t.id) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.name?.takeIf { it.isNotBlank() } ?: t.categoryName ?: "—",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatYenSigned(t.type, t.amount), style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = if (t.type == "income") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddCard(
    templates: List<QuickTemplate>,
    onUseTemplate: (String) -> Unit,
    onAddNew: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dash_quick_title), style = MaterialTheme.typography.titleSmall)
            templates.forEach { t ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onUseTemplate(t.id) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (t.amount != null) {
                        Text(formatYenSigned(t.type, t.amount), style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = onAddNew) { Text(stringResource(R.string.dash_quick_other)) }
        }
    }
}

@Composable
private fun Sparkline(values: List<Long>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val barGap = 2.dp.toPx()
        val barWidth = (size.width - barGap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val barHeight = size.height * (v.toFloat() / max)
            val left = i * (barWidth + barGap)
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            )
        }
    }
}
