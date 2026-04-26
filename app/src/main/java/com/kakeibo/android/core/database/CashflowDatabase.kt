package com.kakeibo.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local replica of the backend Postgres schema. Real entities are added per feature in
 * Phase 2 (sync foundation); for now we keep a single placeholder so the wiring compiles.
 */
@Database(
    entities = [SchemaMarkerEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CashflowDatabase : RoomDatabase()
