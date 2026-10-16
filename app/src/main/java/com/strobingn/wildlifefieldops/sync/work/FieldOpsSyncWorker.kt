package com.strobingn.wildlifefieldops.sync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.strobingn.wildlifefieldops.ui.viewmodel.offlineModeOn
import dagger.hilt.android.EntryPointAccessors

/**
 * Background upload / reconcile. Delegates to [FieldOpsSyncWorkRunner] →
 * [com.strobingn.wildlifefieldops.data.repository.SyncRepository.syncAll].
 */
class FieldOpsSyncWorker(
    appContext: Context,
    private val params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Offline Mode pauses cloud sync, same as Sync Now. Rows stay queued in Room and
        // AutoSync enqueues again when Offline Mode is turned off.
        if (applicationContext.offlineModeOn()) return Result.success()
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            FieldOpsSyncWorkerEntryPoint::class.java
        )
        val operationId = inputData.getString(SyncWorkCorrelation.KEY_OPERATION_ID)
            ?: id.toString()
        val generation = runCatching { generationFromParams() }.getOrDefault(0)
        return when (
            entry.fieldOpsSyncWorkRunner().run(
                operationId = operationId,
                workRequestId = id.toString(),
                generation = generation
            )
        ) {
            FieldOpsSyncWorkOutcome.Success -> Result.success()
            FieldOpsSyncWorkOutcome.Retry -> Result.retry()
        }
    }

    /**
     * WorkManager 2.12 exposes generation on [androidx.work.WorkInfo]. Worker
     * parameters may also carry it; fall back to runAttemptCount.
     */
    private fun generationFromParams(): Int {
        val method = params.javaClass.methods.firstOrNull { it.name == "getGeneration" }
        val value = method?.invoke(params) as? Int
        return value ?: runAttemptCount
    }
}
