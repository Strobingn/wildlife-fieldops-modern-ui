package com.strobingn.wildlifefieldops.sync.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues unique `fieldops-sync` work with [ExistingWorkPolicy.KEEP] and a
 * connected-network constraint. Cancel / update does not clear domain pending.
 */
@Singleton
class FieldOpsSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: FieldOpsSyncEnqueueCoordinator
) {
    fun enqueueSync(): SyncEnqueueResult {
        val workManager = WorkManager.getInstance(context)
        val existingUnfinished = workManager
            .getWorkInfosForUniqueWork(FieldOpsSyncWorkNames.UNIQUE_WORK_NAME)
            .get()
            .any { !it.state.isFinished }

        return when (val decision = coordinator.prepare(existingUnfinished)) {
            SyncEnqueueDecision.Disabled -> SyncEnqueueResult.Disabled
            is SyncEnqueueDecision.KeptExisting -> SyncEnqueueResult.KeptExisting(
                operationId = decision.operation.operationId
            )
            is SyncEnqueueDecision.ReadyToEnqueue -> {
                val request = OneTimeWorkRequestBuilder<FieldOpsSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(
                        Data.Builder().apply {
                            decision.input.forEach { (k, v) -> putString(k, v) }
                        }.build()
                    )
                    .apply { decision.tags.forEach { addTag(it) } }
                    .build()
                coordinator.bindAfterCreate(decision.operation.operationId, request.id.toString())
                workManager.enqueueUniqueWork(
                    FieldOpsSyncWorkNames.UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
                SyncEnqueueResult.Enqueued(
                    operationId = decision.operation.operationId,
                    workRequestId = request.id.toString()
                )
            }
        }
    }

    fun cancelScheduledWork() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(FieldOpsSyncWorkNames.UNIQUE_WORK_NAME)
        coordinator.onWorkCancelled()
    }
}

sealed class SyncEnqueueResult {
    data object Disabled : SyncEnqueueResult()
    data class Enqueued(val operationId: String, val workRequestId: String) : SyncEnqueueResult()
    data class KeptExisting(val operationId: String) : SyncEnqueueResult()
}
