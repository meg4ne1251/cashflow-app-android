package com.kakeibo.android.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakeibo.android.core.data.transaction.TransactionInput
import com.kakeibo.android.core.data.transaction.TransactionRepository
import com.kakeibo.android.core.database.entity.AccountEntity
import com.kakeibo.android.core.database.entity.CategoryEntity
import com.kakeibo.android.core.util.Calculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

data class TransactionFormUiState(
    val isEdit: Boolean = false,
    val type: String = "expense",
    val expr: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val name: String = "",
    val memo: String = "",
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    /** Categories selectable for the current type (income/expense). */
    val typeCategories: List<CategoryEntity> get() = categories.filter { it.type == type }

    /** Evaluated amount of the calculator expression, or null when empty/invalid. */
    val amount: Long? get() = Calculator.evaluateExpression(expr)

    val showComputed: Boolean get() = Calculator.hasOperator(expr) && amount != null
    val canSave: Boolean get() = !saving && (amount?.let { it > 0 } == true) && categoryId != null
}

/**
 * Backs both the create (`transactions/new`, optional `templateId` prefill) and edit
 * (`transactions/{id}/edit`) form. Amount uses the shared [Calculator] expression; the category is
 * required because the backend sync push rejects a transaction without one.
 */
@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val repository: TransactionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editId: String? = savedStateHandle["id"]
    private val templateId: String? = savedStateHandle["templateId"]

    private val _uiState = MutableStateFlow(TransactionFormUiState(isEdit = editId != null))
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeCategories(), repository.observeAccounts()) { cats, accs ->
                cats to accs
            }.collect { (cats, accs) ->
                _uiState.update { it.copy(categories = cats, accounts = accs) }
            }
        }
        prefill()
    }

    private fun prefill() {
        viewModelScope.launch {
            when {
                editId != null -> repository.getTransaction(editId)?.let { tx ->
                    _uiState.update {
                        it.copy(
                            type = tx.type,
                            expr = tx.amount.toString(),
                            categoryId = tx.categoryId,
                            accountId = tx.accountId,
                            dateTime = parseDate(tx.date),
                            name = tx.name.orEmpty(),
                            memo = tx.memo.orEmpty(),
                        )
                    }
                }
                templateId != null -> repository.getTemplate(templateId)?.let { tpl ->
                    _uiState.update {
                        it.copy(
                            type = tpl.type,
                            expr = tpl.amount?.toString().orEmpty(),
                            categoryId = tpl.categoryId,
                            accountId = tpl.accountId,
                            name = tpl.transactionName.orEmpty(),
                            memo = tpl.memo.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    fun onTypeChange(type: String) = _uiState.update {
        // Category lists differ per type, so the selected category is cleared on switch (web parity).
        it.copy(type = type, categoryId = null)
    }

    fun onCategorySelect(id: String) = _uiState.update { it.copy(categoryId = id) }
    fun onAccountToggle(id: String) = _uiState.update {
        it.copy(accountId = if (it.accountId == id) null else id)
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value.take(100)) }
    fun onMemoChange(value: String) = _uiState.update { it.copy(memo = value.take(500)) }

    fun onDateChange(dateTime: LocalDateTime) = _uiState.update { it.copy(dateTime = dateTime) }

    // ----- Calculator pad -----
    fun appendDigit(s: String) = _uiState.update { it.copy(expr = (it.expr + s).take(24)) }

    fun appendOperator(op: Char) = _uiState.update {
        val prev = it.expr
        val next = when {
            prev.isEmpty() -> if (op == '-') "-" else prev
            prev.last() in "+-*/" -> prev.dropLast(1) + op
            else -> prev + op
        }
        it.copy(expr = next)
    }

    fun backspace() = _uiState.update { it.copy(expr = it.expr.dropLast(1)) }

    fun equals() = _uiState.update {
        if (Calculator.hasOperator(it.expr)) {
            Calculator.evaluateExpression(it.expr)?.let { r -> it.copy(expr = r.toString()) } ?: it
        } else it
    }

    fun save() {
        val state = _uiState.value
        val amount = state.amount
        if (state.saving || amount == null || amount <= 0 || state.categoryId == null) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val input = TransactionInput(
                type = state.type,
                amount = amount,
                date = state.dateTime.format(DATE_FORMAT),
                categoryId = state.categoryId,
                accountId = state.accountId,
                name = state.name.trim().ifBlank { null },
                memo = state.memo.trim().ifBlank { null },
            )
            try {
                if (editId != null) repository.updateTransaction(editId, input)
                else repository.createTransaction(input)
                _uiState.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                // A write failure must re-enable the form; leaving saving=true would lock the button.
                Timber.e(e, "Saving transaction failed")
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    private fun parseDate(value: String): LocalDateTime =
        runCatching { LocalDateTime.parse(value) }.getOrElse { LocalDateTime.now() }
}
