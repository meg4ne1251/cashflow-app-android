package com.kakeibo.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.kakeibo.android.R
import com.kakeibo.android.core.data.transaction.TransactionSort
import com.kakeibo.android.core.database.dao.TransactionListItem
import com.kakeibo.android.core.database.entity.SyncStatus
import com.kakeibo.android.core.util.formatYenSigned
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

@Composable
fun TransactionListScreen(
    onAddNew: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var advancedOpen by remember { mutableStateOf(false) }

    val deletedMessage = stringResource(R.string.tx_deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tx_new))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = filter.keyword.orEmpty(),
                    onValueChange = viewModel::setKeyword,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.tx_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypePill(stringResource(R.string.tx_filter_all), filter.type == null) { viewModel.setType(null) }
                    TypePill(stringResource(R.string.tx_type_expense), filter.type == "expense") { viewModel.setType("expense") }
                    TypePill(stringResource(R.string.tx_type_income), filter.type == "income") { viewModel.setType("income") }
                    TextButton(onClick = { advancedOpen = !advancedOpen }) {
                        Text(stringResource(R.string.tx_filter_advanced))
                    }
                }
                if (advancedOpen) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterDropdown(
                            label = stringResource(R.string.tx_filter_category),
                            selectedLabel = categories.firstOrNull { it.id == filter.categoryId }?.name,
                            options = listOf(null to stringResource(R.string.tx_filter_all)) +
                                categories.map { it.id to it.name },
                            onSelect = viewModel::setCategory,
                        )
                        FilterDropdown(
                            label = stringResource(R.string.tx_filter_account),
                            selectedLabel = accounts.firstOrNull { it.id == filter.accountId }?.name,
                            options = listOf(null to stringResource(R.string.tx_filter_all)) +
                                accounts.map { it.id to it.name },
                            onSelect = viewModel::setAccount,
                        )
                    }
                    SortDropdown(current = filter.sort, onSelect = viewModel::setSort)
                }
            }

            val isEmpty = items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0
            if (isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.tx_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Day headers only make sense when rows are ordered by date; under amount sorts they
                // would appear between nearly every row.
                val showDayHeaders = filter.sort == TransactionSort.DATE_DESC ||
                    filter.sort == TransactionSort.DATE_ASC
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val item = items[index] ?: return@items
                        val prev = if (index > 0) items.peek(index - 1) else null
                        if (showDayHeaders && (prev == null || dayKey(prev.date) != dayKey(item.date))) {
                            DayHeader(item.date)
                        }
                        TransactionRow(
                            item = item,
                            onClick = { onEdit(item.id) },
                            onDelete = {
                                scope.launch {
                                    val snapshot = viewModel.softDelete(item.id) ?: return@launch
                                    val result = snackbarHostState.showSnackbar(
                                        message = deletedMessage,
                                        actionLabel = undoLabel,
                                        // Auto-dismiss closes the undo window; commitDelete then pushes.
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete(snapshot)
                                    } else {
                                        viewModel.commitDelete(snapshot.id)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypePill(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selectedLabel: String?,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedLabel ?: label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SortDropdown(current: TransactionSort, onSelect: (TransactionSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        TransactionSort.DATE_DESC to stringResource(R.string.tx_sort_date_desc),
        TransactionSort.DATE_ASC to stringResource(R.string.tx_sort_date_asc),
        TransactionSort.AMOUNT_DESC to stringResource(R.string.tx_sort_amount_desc),
        TransactionSort.AMOUNT_ASC to stringResource(R.string.tx_sort_amount_asc),
    )
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.tx_sort_label) + ": " + labels[current].orEmpty())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (sort, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(sort)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(date: String) {
    val day = runCatching { LocalDate.parse(date.substring(0, 10)) }.getOrNull()
    val text = day?.let {
        it.format(HEADER_FORMAT) + " (" + it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPANESE) + ")"
    } ?: date
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun TransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(parseColor(item.categoryColor) ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name?.takeIf { it.isNotBlank() }
                    ?: item.categoryName
                    ?: if (item.type == "income") stringResource(R.string.tx_type_income) else stringResource(R.string.tx_type_expense),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            val meta = listOfNotNull(item.categoryName, item.accountName, item.memo?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SyncBadge(item.syncStatus)
        }
        Text(
            text = formatYenSigned(item.type, item.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = if (item.type == "income") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = { menuOpen = false; onClick() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun SyncBadge(status: String) {
    val label = when (status) {
        SyncStatus.PENDING -> stringResource(R.string.sync_pending)
        SyncStatus.CONFLICT -> stringResource(R.string.sync_conflict)
        else -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .padding(top = 2.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private fun parseColor(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

private fun dayKey(date: String): String = date.take(10)
