package com.strobingn.wildlifefieldops.sync.work

/**
 * Business-truth states for one sync **operation** (a batch that calls
 * [com.strobingn.wildlifefieldops.data.repository.SyncRepository.syncAll]).
 *
 * This is **not** a WorkManager [androidx.work.ListenableWorker.Result].
 * `Result.success` is scheduler completion, not exactly-once ACK.
 */
enum class DomainSyncState {
    PENDING,
    READY,
    UPLOADING,
    ACKNOWLEDGED,
    CONFLICT,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE
}

data class DomainSyncOperation(
    val operationId: String,
    val idempotencyKey: String,
    val state: DomainSyncState,
    val workRequestId: String? = null,
    val workGeneration: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Durable domain ledger. Independent of WorkManager's ~7-day metrics store.
 * Cancel / KEEP / REPLACE of unique work must not delete these rows or Room
 * `isSynced` queues.
 */
interface DomainSyncLedger {
    fun beginOrReuseActive(
        idempotencyKey: String = FieldOpsSyncWorkNames.ACTIVE_IDEMPOTENCY_KEY,
        nowMs: Long = System.currentTimeMillis()
    ): DomainSyncOperation

    fun bindWorkRequest(operationId: String, workRequestId: String, generation: Int, nowMs: Long = System.currentTimeMillis())

    fun transition(operationId: String, state: DomainSyncState, nowMs: Long = System.currentTimeMillis()): DomainSyncOperation?

    fun get(operationId: String): DomainSyncOperation?

    fun findActive(): DomainSyncOperation?

    fun snapshot(): List<DomainSyncOperation>

    /**
     * Work was cancelled or updated. Domain pending stays; in-flight rows
     * return to [DomainSyncState.READY] so the next enqueue can resume.
     */
    fun revertInFlightToReady(nowMs: Long = System.currentTimeMillis())
}

object FieldOpsSyncWorkNames {
    /** Unique work name passed to [androidx.work.WorkManager.enqueueUniqueWork]. */
    const val UNIQUE_WORK_NAME = "fieldops-sync"

    /**
     * [androidx.work.ExistingWorkPolicy.KEEP]: if unique work is still unfinished,
     * ignore the new request. Domain pending lives in Room regardless.
     * REPLACE would cancel an in-flight upload without clearing `isSynced=0`.
     */
    const val EXISTING_WORK_POLICY = "KEEP"

    const val ACTIVE_IDEMPOTENCY_KEY = "fieldops-sync"

    const val WORKER_CLASS_TAG = "fo-sync"

    fun shouldSkipBecauseKeep(existingUnfinished: Boolean): Boolean = existingUnfinished
}

class InMemoryDomainSyncLedger : DomainSyncLedger {
    private val lock = Any()
    private val byId = LinkedHashMap<String, DomainSyncOperation>()

    override fun beginOrReuseActive(idempotencyKey: String, nowMs: Long): DomainSyncOperation = synchronized(lock) {
        findActiveLocked(idempotencyKey)?.let { return it }
        val operation = DomainSyncOperation(
            operationId = newId(),
            idempotencyKey = SyncWorkCorrelation.boundToken(idempotencyKey),
            state = DomainSyncState.READY,
            createdAt = nowMs,
            updatedAt = nowMs
        )
        byId[operation.operationId] = operation
        operation
    }

    override fun bindWorkRequest(
        operationId: String,
        workRequestId: String,
        generation: Int,
        nowMs: Long
    ) {
        synchronized(lock) {
            val current = byId[operationId] ?: return
            byId[operationId] = current.copy(
                workRequestId = workRequestId,
                workGeneration = generation,
                updatedAt = nowMs
            )
        }
    }

    override fun transition(operationId: String, state: DomainSyncState, nowMs: Long): DomainSyncOperation? =
        synchronized(lock) {
            val current = byId[operationId] ?: return null
            val next = current.copy(state = state, updatedAt = nowMs)
            byId[operationId] = next
            next
        }

    override fun get(operationId: String): DomainSyncOperation? = synchronized(lock) { byId[operationId] }

    override fun findActive(): DomainSyncOperation? = synchronized(lock) {
        findActiveLocked(FieldOpsSyncWorkNames.ACTIVE_IDEMPOTENCY_KEY)
    }

    override fun snapshot(): List<DomainSyncOperation> = synchronized(lock) { byId.values.toList() }

    override fun revertInFlightToReady(nowMs: Long) {
        synchronized(lock) {
            byId.entries.forEach { (id, op) ->
                if (op.state == DomainSyncState.UPLOADING || op.state == DomainSyncState.PENDING) {
                    byId[id] = op.copy(state = DomainSyncState.READY, updatedAt = nowMs)
                }
            }
        }
    }

    private fun findActiveLocked(idempotencyKey: String): DomainSyncOperation? =
        byId.values.lastOrNull {
            it.idempotencyKey == idempotencyKey && it.state in ACTIVE
        }

    private fun newId(): String = java.util.UUID.randomUUID().toString()

    companion object {
        private val ACTIVE = setOf(
            DomainSyncState.PENDING,
            DomainSyncState.READY,
            DomainSyncState.UPLOADING,
            DomainSyncState.RETRYABLE_FAILURE
        )
    }
}
