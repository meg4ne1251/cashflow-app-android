package com.kakeibo.android.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 0 placeholder. Room requires at least one entity; this gets replaced in
 * Phase 2 when the real schema is added.
 */
@Entity(tableName = "schema_marker")
data class SchemaMarkerEntity(
    @PrimaryKey val id: Int = 0,
    val schemaVersion: Int = 1
)
