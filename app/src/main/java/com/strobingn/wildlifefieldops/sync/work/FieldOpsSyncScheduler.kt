package com.strobingn.wildlifefieldops.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues unique `fieldops-sync` work with [ExistingWorkPolicy.APPEND_OR_REPLACE]
 * (chain behind an in-flight upload; replace a failed/cancelled chain) and a
 * connected-network constraint. Cancel does not clear domain pending / isSynced.
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
                    .setConstraints(networkConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
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
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    request
                )
                SyncEnqueueResult.Enqueued(
                    operationId = decision.operation.operationId,
                    workRequestId = request.id.toString()
                )
            }
        }
    }

    fun enqueuePeriodicSafetyNet() {
        val request = PeriodicWorkRequestBuilder<FieldOpsSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(FieldOpsSyncWorkNames.PERIODIC_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FieldOpsSyncWorkNames.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelScheduledWork() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(FieldOpsSyncWorkNames.UNIQUE_WORK_NAME)
        coordinator.onWorkCancelled()
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}

sealed class SyncEnqueueResult {
    data object Disabled : SyncEnqueueResult()
    data class Enqueued(val operationId: String, val workRequestId: String) : SyncEnqueueResult()
    data class KeptExisting(val operationId: String) : SyncEnqueueResult()
}
