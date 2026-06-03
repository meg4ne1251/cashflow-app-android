package com.kakeibo.android.core.data.dashboard

import com.kakeibo.android.core.database.dao.AccountDao
import com.kakeibo.android.core.database.dao.BudgetDao
import com.kakeibo.android.core.database.dao.CategoryDao
import com.kakeibo.android.core.database.dao.TemplateDao
import com.kakeibo.android.core.database.dao.TransactionDao
import com.kakeibo.android.core.database.dao.TransactionListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One account's display summary on the dashboard (balance is the server-computed value). */
data class AccountSummary(
    val id: String,
    val name: String,
    val type: String,
    val balance: Long,
    val paymentDay: Int?,
)

/** A budget row with its current-month spend, for the consumption list. */
data class BudgetConsumption(
    val categoryId: String,
    val categoryName: String,
    val budget: Long,
    val spent: Long,
    val rate: Double, // 0.0..1.0+
)

/** A template surfaced as a one-tap quick-add on the dashboard. */
data class QuickTemplate(
    val id: String,
    val name: String,
    val type: String,
    val amount: Long?,
)

/** Everything the dashboard renders, derived entirely from the local Room replica (offline-first). */
data class DashboardUiData(
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val balance: Long = 0,
    val incomeChangeRate: Double? = null,
    val expenseChangeRate: Double? = null,
    val budgetTotal: Long = 0,
    val budgetConsumption: List<BudgetConsumption> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val recent: List<TransactionListItem> = emptyList(),
    val quickTemplates: List<QuickTemplate> = emptyList(),
    val spark: List<Long> = emptyList(),
)

private data class Totals(
    val income: Long,
    val expense: Long,
    val incomeChange: Double?,
    val expenseChange: Double?,
)

/**
 * Computes the dashboard from local data so it stays correct offline and updates the instant a
 * transaction is written. Account balances come from the server-computed [AccountEntity.balance]
 * (refreshed on the next sync); monthly totals/consumption are aggregated live from local rows.
 * Savings goals are not part of the sync contract, so they are omitted (vs. the web dashboard).
 */
@Singleton
class DashboardRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val templateDao: TemplateDao,
) {

    fun observeDashboard(): Flow<DashboardUiData> {
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1)
        val prevStart = monthStart.minusMonths(1)
        val sparkStart = today.minusDays(SPARK_DAYS - 1L)

        val monthStartTs = startOfDay(monthStart)
        val monthEndTs = startOfDay(monthEnd)
        val prevStartTs = startOfDay(prevStart)
        val prevEndTs = monthStartTs
        val sparkStartTs = startOfDay(sparkStart)
        val yearMonth = "%04d-%02d".format(today.year, today.monthValue)

        val totals = combine(
            transactionDao.observeSumByType("income", monthStartTs, monthEndTs),
            transactionDao.observeSumByType("expense", monthStartTs, monthEndTs),
            transactionDao.observeSumByType("income", prevStartTs, prevEndTs),
            transactionDao.observeSumByType("expense", prevStartTs, prevEndTs),
        ) { incMonth, expMonth, incPrev, expPrev ->
            Totals(
                income = incMonth,
                expense = expMonth,
                incomeChange = changeRate(incMonth, incPrev),
                expenseChange = changeRate(expMonth, expPrev),
            )
        }

        val budgets = combine(
            budgetDao.observeAll(),
            categoryDao.observeAll(),
            transactionDao.observeCategorySpending(monthStartTs, monthEndTs),
        ) { budgetRows, categories, spending ->
            val names = categories.associate { it.id to it.name }
            val spentByCategory = spending.associate { it.categoryId to it.spent }
            budgetRows
                .filter { it.yearMonth == yearMonth }
                .map { b ->
                    val spent = spentByCategory[b.categoryId] ?: 0L
                    BudgetConsumption(
                        categoryId = b.categoryId,
                        categoryName = names[b.categoryId] ?: "—",
                        budget = b.amount,
                        spent = spent,
                        rate = if (b.amount > 0) spent.toDouble() / b.amount else 0.0,
                    )
                }
                .sortedByDescending { it.rate }
        }

        val accounts = accountDao.observeAll().let { flow ->
            combine(flow, transactionDao.observeRecent(RECENT_LIMIT)) { accountRows, recent ->
                accountRows.map {
                    AccountSummary(it.id, it.name, it.type, it.balance, it.paymentDay)
                } to recent
            }
        }

        val extras = combine(
            templateDao.observeAll(),
            transactionDao.observeExpensesSince(sparkStartTs),
        ) { templates, expenses ->
            val quick = templates
                .filter { it.amount != null }
                .take(QUICK_LIMIT)
                .map { QuickTemplate(it.id, it.name, it.type, it.amount) }
            quick to buildSpark(expenses.map { it.date to it.amount }, sparkStart)
        }

        return combine(totals, budgets, accounts, extras) { t, budgetList, accAndRecent, quickAndSpark ->
            val (accountSummaries, recent) = accAndRecent
            val (quick, spark) = quickAndSpark
            DashboardUiData(
                monthIncome = t.income,
                monthExpense = t.expense,
                balance = t.income - t.expense,
                incomeChangeRate = t.incomeChange,
                expenseChangeRate = t.expenseChange,
                budgetTotal = budgetList.sumOf { it.budget },
                budgetConsumption = budgetList,
                accounts = accountSummaries,
                recent = recent,
                quickTemplates = quick,
                spark = spark,
            )
        }
    }

    private fun buildSpark(expenses: List<Pair<String, Long>>, sparkStart: LocalDate): List<Long> {
        val buckets = LongArray(SPARK_DAYS)
        for ((dateStr, amount) in expenses) {
            val day = runCatching { LocalDate.parse(dateStr.substring(0, 10)) }.getOrNull() ?: continue
            val idx = ChronoUnit.DAYS.between(sparkStart, day).toInt()
            if (idx in 0 until SPARK_DAYS) buckets[idx] += amount
        }
        return buckets.toList()
    }

    private fun changeRate(current: Long, previous: Long): Double? =
        if (previous == 0L) null else (current - previous).toDouble() / previous

    private fun startOfDay(date: LocalDate): String = "${date}T00:00:00"

    private companion object {
        const val SPARK_DAYS = 30
        const val RECENT_LIMIT = 6
        const val QUICK_LIMIT = 5
    }
}
