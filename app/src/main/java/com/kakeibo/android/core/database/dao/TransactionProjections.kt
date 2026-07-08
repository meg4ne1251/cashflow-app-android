package com.kakeibo.android.core.database.dao

/**
 * Read-only query projections for the transactions feature. These are not Room entities; they are
 * the shapes returned by the JOIN/aggregate queries on [TransactionDao]. Column aliases in those
 * queries match these field names directly, so no @ColumnInfo is needed.
 */

/** A transaction row joined with its (optional) category and account, for the list UI. */
data class TransactionListItem(
    val id: String,
    val name: String?,
    val type: String,
    val amount: Long,
    val date: String,
    val memo: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val accountId: String?,
    val accountName: String?,
    val syncStatus: String,
)

/** Expense total per category, for dashboard budget-consumption aggregation. */
data class CategorySpent(
    val categoryId: String,
    val spent: Long,
)

/** A transaction's date + amount, used to build the 30-day spend sparkline. */
data class DateAmount(
    val date: String,
    val amount: Long,
)
