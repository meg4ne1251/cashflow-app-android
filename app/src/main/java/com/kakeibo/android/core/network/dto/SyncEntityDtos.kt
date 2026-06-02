package com.kakeibo.android.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the entities delivered by `GET /sync/pull`. Field names match the backend
 * JSON (snake_case) so no @SerialName is needed. Only the fields persisted locally are
 * declared; nested objects (category/account) and tag arrays are ignored on purpose
 * (`ignoreUnknownKeys = true`).
 */

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val type: String,
    val initial_balance: Long,
    val currency: String,
    val sort_order: Int,
    val payment_day: Int? = null,
    val balance: Long,
    val version: Int,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val type: String,
    val icon: String? = null,
    val color: String? = null,
    val sort_order: Int,
    val is_default: Boolean,
    val version: Int,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

@Serializable
data class TagDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val version: Int,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

@Serializable
data class TransactionDto(
    val id: String,
    val name: String? = null,
    val type: String,
    val amount: Long,
    val currency: String,
    val date: String,
    val memo: String? = null,
    val category_id: String? = null,
    val account_id: String? = null,
    val is_auto_generated: Boolean = false,
    val is_balance_adjustment: Boolean = false,
    val recurring_transaction_id: String? = null,
    val version: Int,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

@Serializable
data class TransferDto(
    val id: String,
    val from_account_id: String,
    val to_account_id: String,
    val amount: Long,
    val currency: String,
    val date: String,
    val memo: String? = null,
    val version: Int,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

@Serializable
data class TemplateDto(
    val id: String,
    val name: String,
    val transaction_name: String? = null,
    val type: String,
    val amount: Long? = null,
    val currency: String,
    val category_id: String? = null,
    val account_id: String? = null,
    val memo: String? = null,
    val use_count: Int,
    val last_used_at: String? = null,
    val version: Int,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

@Serializable
data class RecurringTransactionDto(
    val id: String,
    val type: String,
    val amount: Long,
    val currency: String,
    val category_id: String,
    val account_id: String,
    val memo: String? = null,
    val frequency: String,
    val interval: Int,
    val day_of_week: Int? = null,
    val day_of_month: Int? = null,
    val month_of_year: Int? = null,
    val start_date: String,
    val end_date: String? = null,
    val next_execution_date: String,
    val is_active: Boolean,
    val version: Int,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

@Serializable
data class BudgetDto(
    val id: String,
    val category_id: String,
    val year_month: String,
    val amount: Long,
    val currency: String,
    val version: Int,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

@Serializable
data class NotificationSettingDto(
    val id: String,
    val type: String,
    val is_enabled: Boolean,
    val frequency: String? = null,
    val day_of_week: String? = null,
    val time_of_day: String? = null,
    val threshold_percent: Int? = null,
    val reminder_days_before: Int? = null,
    val version: Int,
    val created_at: String,
    val updated_at: String,
)

@Serializable
data class InputPatternDto(
    val id: String,
    val keyword: String,
    val category_id: String? = null,
    val account_id: String? = null,
    val hit_count: Int,
    val last_used_at: String,
)
