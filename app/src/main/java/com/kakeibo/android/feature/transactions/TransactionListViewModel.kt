package com.kakeibo.android.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kakeibo.android.core.data.transaction.TransactionFilter
import com.kakeibo.android.core.data.transaction.TransactionRepository
import com.kakeibo.android.core.data.transaction.TransactionSort
import com.kakeibo.android.core.database.dao.TransactionListItem
import com.kakeibo.android.core.database.entity.AccountEntity
import com.kakeibo.android.core.database.entity.CategoryEntity
import com.kakeibo.android.core.database.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Drives the paged transaction list and its filters. Deletes use a deferred-commit undo: the row is
 * hidden locally immediately and the snapshot is returned so the screen can either [undoDelete]
 * (revert locally, no server round-trip) or [commitDelete] (flush the delete) when the snackbar ends.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val repository: TransactionRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionFilter())
    val filter: StateFlow<TransactionFilter> = _filter.asStateFlow()

    val items: Flow<PagingData<TransactionListItem>> =
        _filter
            // Debounce only while a search term is being typed; chip/sort taps stay instant so the
            // Pager + PagingSource aren't rebuilt on every keystroke.
            .debounce { if (it.keyword.isNullOrBlank()) 0L else KEYWORD_DEBOUNCE_MS }
            .distinctUntilChanged()
            .flatMapLatest { repository.pagedTransactions(it) }
            .cachedIn(viewModelScope)

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setKeyword(value: String) = _filter.update { it.copy(keyword = value) }
    fun setType(type: String?) = _filter.update { it.copy(type = type) }
    fun setCategory(categoryId: String?) = _filter.update { it.copy(categoryId = categoryId) }
    fun setAccount(accountId: String?) = _filter.update { it.copy(accountId = accountId) }
    fun setSort(sort: TransactionSort) = _filter.update { it.copy(sort = sort) }

    suspend fun softDelete(id: String): TransactionEntity? = repository.softDeleteLocal(id)
    suspend fun undoDelete(snapshot: TransactionEntity) = repository.restore(snapshot)
    suspend fun commitDelete(id: String) = repository.commitDelete(id)

    private companion object {
        const val KEYWORD_DEBOUNCE_MS = 250L
    }
}
