package com.strobingn.wildlifefieldops.sync.work

/**
 * Flag-aware unique-work enqueue rules. Android-free so KEEP / duplicate
 * idempotency can be unit-tested without WorkManager.
 */
class FieldOpsSyncEnqueueCoordinator(
    private val autoSync: AutoSyncGate,
    private val ledger: DomainSyncLedger,
    private val adapter: WorkAnalyticsAdapter
) {
    fun prepare(
        existingUnfinished: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): SyncEnqueueDecision {
        if (!autoSync.isEnabled()) return SyncEnqueueDecision.Disabled
        // existingUnfinished is unused: APPEND_OR_REPLACE chains behind in-flight
        // work instead of KEEP-skipping, so a write during upload still syncs.
        @Suppress("UNUSED_PARAMETER")
        val ignored = existingUnfinished

        val operation = ledger.beginOrReuseActive(nowMs = nowMs)
        val tags = SyncWorkCorrelation.tagsFor(operation)
        val input = SyncWorkCorrelation.inputFor(operation)

        adapter.record(
            SchedulerEvent(
                type = SchedulerEventType.ENQUEUE,
                workRequestId = operation.workRequestId,
                generation = operation.workGeneration ?: 0,
                tags = tags,
                timestampMs = nowMs
            )
        )
        return SyncEnqueueDecision.ReadyToEnqueue(
            operation = operation,
            tags = tags,
            input = input
        )
    }

    fun bindAfterCreate(operationId: String, workRequestId: String, generation: Int = 0) {
        ledger.bindWorkRequest(operationId, workRequestId, generation)
    }

    fun onWorkCancelled() {
        ledger.revertInFlightToReady()
        // Room isSynced / observation queues are left untouched.
    }
}

sealed class SyncEnqueueDecision {
    data object Disabled : SyncEnqueueDecision()

    data class ReadyToEnqueue(
        val operation: DomainSyncOperation,
        val tags: Set<String>,
        val input: Map<String, String>
    ) : SyncEnqueueDecision()

    data class KeptExisting(
        val operation: DomainSyncOperation,
        val tags: Set<String>,
        val input: Map<String, String>
    ) : SyncEnqueueDecision()
}
