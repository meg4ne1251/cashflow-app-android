package com.kakeibo.android.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background full sync (design §5.3). Runs the same push→pull as [SyncManager] but under
 * WorkManager so offline writes are flushed when connectivity returns and a periodic cadence
 * keeps the replica fresh. A transport failure returns [Result.retry] so WorkManager backs off
 * and tries again; the watermark and pending rows are untouched on failure, so retries are safe.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (syncEngine.sync()) {
        is SyncOutcome.Success -> Result.success()
        is SyncOutcome.Error -> Result.retry()
    }
}
