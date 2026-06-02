package com.kakeibo.android.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Sync envelope DTOs, mirroring the backend `com.kakeibo.shared.model` sync contract.
 *
 * Phase 2 uses [SyncPullResponse] only (initial full sync). The push types are declared
 * now so the API surface is complete, but the write path is wired up in Phase 3 alongside
 * local CRUD.
 */

@Serializable
data class SyncPullResponse(
    val data: SyncData,
    val sync_timestamp: String,
    val has_more: Boolean,
)

@Serializable
data class SyncData(
    val transactions: List<TransactionDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val accounts: List<AccountDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    val recurring_transactions: List<RecurringTransactionDto> = emptyList(),
    val budgets: List<BudgetDto> = emptyList(),
    val transfers: List<TransferDto> = emptyList(),
    val notification_settings: List<NotificationSettingDto> = emptyList(),
    val input_patterns: List<InputPatternDto> = emptyList(),
)

@Serializable
data class SyncPushRequest(
    val changes: List<SyncChange>,
)

@Serializable
data class SyncChange(
    val entity_type: String,
    val operation: String,
    val data: JsonObject,
    val client_version: Int,
)

@Serializable
data class SyncPushResponse(
    val results: List<SyncResult>,
)

@Serializable
data class SyncResult(
    val entity_type: String,
    val id: String,
    val status: String,
    val server_version: Int? = null,
    val server_data: JsonObject? = null,
)
