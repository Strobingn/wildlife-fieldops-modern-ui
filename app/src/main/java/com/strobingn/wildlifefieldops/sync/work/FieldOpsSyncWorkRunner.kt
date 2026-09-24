package com.strobingn.wildlifefieldops.sync.work

/**
 * Domain side of [FieldOpsSyncWorker]. Unit-testable without WorkManager.
 *
 * ACKNOWLEDGED is written only after [FieldOpsSyncGateway.syncAll] returns
 * `success = true`. Worker `Result.success` is decided from this outcome
 * and is not itself domain proof.
 */
class FieldOpsSyncWorkRunner(
    private val gateway: FieldOpsSyncGateway,
    private val ledger: DomainSyncLedger,
    private val adapter: WorkAnalyticsAdapter
) {
    suspend fun run(
        operationId: String,
        workRequestId: String,
        generation: Int,
        nowMs: Long = System.currentTimeMillis()
    ): FieldOpsSyncWorkOutcome {
        val operation = ledger.get(operationId)
            ?: ledger.beginOrReuseActive(nowMs = nowMs).also {
                ledger.bindWorkRequest(it.operationId, workRequestId, generation, nowMs)
            }
        val boundId = operation.operationId
        ledger.bindWorkRequest(boundId, workRequestId, generation, nowMs)
        ledger.transition(boundId, DomainSyncState.UPLOADING, nowMs)
        adapter.record(
            SchedulerEvent(
                type = SchedulerEventType.START,
                workRequestId = workRequestId,
                generation = generation,
                tags = SyncWorkCorrelation.tagsFor(ledger.get(boundId) ?: operation),
                timestampMs = nowMs
            )
        )

        val result = try {
            gateway.syncAll()
        } catch (_: Throwable) {
            return failRetryable(boundId, workRequestId, generation, nowMs)
        }

        return if (result.success) {
            ledger.transition(boundId, DomainSyncState.ACKNOWLEDGED, nowMs)
            adapter.record(
                SchedulerEvent(
                    type = SchedulerEventType.FINISH,
                    workRequestId = workRequestId,
                    generation = generation,
                    tags = SyncWorkCorrelation.tagsFor(ledger.get(boundId) ?: operation),
                    timestampMs = nowMs
                )
            )
            FieldOpsSyncWorkOutcome.Success
        } else {
            failRetryable(boundId, workRequestId, generation, nowMs)
        }
    }

    private fun failRetryable(
        operationId: String,
        workRequestId: String,
        generation: Int,
        nowMs: Long
    ): FieldOpsSyncWorkOutcome {
        ledger.transition(operationId, DomainSyncState.RETRYABLE_FAILURE, nowMs)
        adapter.record(
            SchedulerEvent(
                type = SchedulerEventType.RETRY,
                workRequestId = workRequestId,
                generation = generation,
                tags = ledger.get(operationId)?.let(SyncWorkCorrelation::tagsFor).orEmpty(),
                timestampMs = nowMs
            )
        )
        return FieldOpsSyncWorkOutcome.Retry
    }
}

enum class FieldOpsSyncWorkOutcome {
    Success,
    Retry
}
