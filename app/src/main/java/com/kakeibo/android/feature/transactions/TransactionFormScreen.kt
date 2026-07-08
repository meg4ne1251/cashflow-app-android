package com.kakeibo.android.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kakeibo.android.R
import com.kakeibo.android.core.util.formatYen
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd (E) HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(
    onClose: () -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEdit) R.string.tx_form_title_edit else R.string.tx_form_title_new
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Type toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = state.type == "expense",
                    onClick = { viewModel.onTypeChange("expense") },
                    label = { Text(stringResource(R.string.tx_type_expense)) },
                )
                FilterChip(
                    selected = state.type == "income",
                    onClick = { viewModel.onTypeChange("income") },
                    label = { Text(stringResource(R.string.tx_type_income)) },
                )
            }

            // Amount display
            val signPrefix = if (state.type == "income") "+¥" else "−¥"
            Text(
                text = signPrefix + (state.expr.ifEmpty { "0" }),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            if (state.showComputed) {
                Text(
                    text = "= " + formatYen(state.amount ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            CalculatorPad(
                onDigit = viewModel::appendDigit,
                onOperator = viewModel::appendOperator,
                onBackspace = viewModel::backspace,
                onEquals = viewModel::equals,
            )

            // Category (required)
            FieldLabel(stringResource(R.string.tx_field_category))
            if (state.typeCategories.isEmpty()) {
                Text(
                    stringResource(R.string.tx_no_categories),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.typeCategories.forEach { c ->
                        FilterChip(
                            selected = state.categoryId == c.id,
                            onClick = { viewModel.onCategorySelect(c.id) },
                            label = { Text(c.name) },
                        )
                    }
                }
            }

            // Account (optional)
            FieldLabel(stringResource(R.string.tx_field_account))
            if (state.accounts.isEmpty()) {
                Text(
                    stringResource(R.string.tx_no_accounts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.accounts.forEach { a ->
                        FilterChip(
                            selected = state.accountId == a.id,
                            onClick = { viewModel.onAccountToggle(a.id) },
                            label = { Text(a.name) },
                        )
                    }
                }
            }

            // Date
            FieldLabel(stringResource(R.string.tx_field_date))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(state.dateTime.format(DISPLAY_FORMAT))
                }
                TextButton(onClick = {
                    viewModel.onDateChange(LocalDateTime.of(java.time.LocalDate.now(), state.dateTime.toLocalTime()))
                }) { Text(stringResource(R.string.tx_date_today)) }
                TextButton(onClick = {
                    viewModel.onDateChange(
                        LocalDateTime.of(java.time.LocalDate.now().minusDays(1), state.dateTime.toLocalTime())
                    )
                }) { Text(stringResource(R.string.tx_date_yesterday)) }
            }

            // Name / Memo
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.tx_field_name)) },
                placeholder = { Text(stringResource(R.string.tx_field_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.memo,
                onValueChange = viewModel::onMemoChange,
                label = { Text(stringResource(R.string.tx_field_memo)) },
                placeholder = { Text(stringResource(R.string.tx_field_memo_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (state.isEdit) R.string.tx_save_edit else R.string.tx_save_new))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.onDateChange(LocalDateTime.of(date, state.dateTime.toLocalTime()))
                    }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CalculatorPad(
    onDigit: (String) -> Unit,
    onOperator: (Char) -> Unit,
    onBackspace: () -> Unit,
    onEquals: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("7", "8", "9", "−"),
        listOf("00", "0", "⌫", "+"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val isOp = key in listOf("÷", "×", "−", "+")
                    CalcKey(
                        label = key,
                        isAccent = isOp || key == "⌫",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "÷" -> onOperator('/')
                                "×" -> onOperator('*')
                                "−" -> onOperator('-')
                                "+" -> onOperator('+')
                                "⌫" -> onBackspace()
                                else -> onDigit(key)
                            }
                        },
                    )
                }
            }
        }
        CalcKey(
            label = stringResource(R.string.calc_equals),
            isAccent = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onEquals,
        )
    }
}

@Composable
private fun CalcKey(
    label: String,
    isAccent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (isAccent) {
        Button(onClick = onClick, modifier = modifier.height(52.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) { Text(label) }
    }
}
