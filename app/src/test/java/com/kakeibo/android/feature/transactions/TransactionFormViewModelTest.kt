package com.kakeibo.android.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.kakeibo.android.core.data.sync.SyncScheduler
import com.kakeibo.android.core.data.transaction.TransactionRepository
import com.kakeibo.android.core.database.CashflowDatabase
import com.kakeibo.android.core.database.entity.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransactionFormViewModelTest {

    private lateinit var db: CashflowDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            CashflowDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TransactionRepository(
            transactionDao = db.transactionDao(),
            categoryDao = db.categoryDao(),
            accountDao = db.accountDao(),
            templateDao = db.templateDao(),
            syncScheduler = NoopScheduler,
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `save is disabled until amount and category are set, then persists a pending create`() = runTest {
        val viewModel = TransactionFormViewModel(repository, SavedStateHandle())

        assertFalse(viewModel.uiState.value.canSave)

        viewModel.appendDigit("5")
        viewModel.appendDigit("0")
        viewModel.appendDigit("0")
        assertEquals(500L, viewModel.uiState.value.amount)
        assertFalse(viewModel.uiState.value.canSave) // category still missing

        viewModel.onCategorySelect("c1")
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.save()

        val dirty = db.transactionDao().dirty()
        assertEquals(1, dirty.size)
        assertEquals(500L, dirty[0].amount)
        assertEquals("c1", dirty[0].categoryId)
        assertEquals(0, dirty[0].version) // never-synced create
        assertEquals(SyncStatus.PENDING, dirty[0].sync.syncStatus)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `calculator evaluates the expression into the amount`() = runTest {
        val viewModel = TransactionFormViewModel(repository, SavedStateHandle())

        "120".forEach { viewModel.appendDigit(it.toString()) }
        viewModel.appendOperator('+')
        "80".forEach { viewModel.appendDigit(it.toString()) }
        viewModel.equals()

        assertEquals("200", viewModel.uiState.value.expr)
        assertEquals(200L, viewModel.uiState.value.amount)
    }

    private object NoopScheduler : SyncScheduler {
        override fun requestSync() = Unit
        override fun ensurePeriodicSync() = Unit
    }
}
