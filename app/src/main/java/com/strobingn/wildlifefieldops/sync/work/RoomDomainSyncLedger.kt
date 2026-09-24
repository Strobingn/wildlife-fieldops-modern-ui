package com.strobingn.wildlifefieldops.sync.work

import com.strobingn.wildlifefieldops.data.local.SyncOperationDao
import com.strobingn.wildlifefieldops.data.local.SyncOperationRecord
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [DomainSyncLedger]. Inserts use IGNORE so a duplicate
 * idempotency key that races with an active row does not create a second
 * in-flight operation.
 *
 * The interface is synchronous so unit tests can use [InMemoryDomainSyncLedger]
 * without a dispatcher. Room calls run on the caller thread via [runBlocking]
 * — workers and the scheduler already sit on background threads.
 */
@Singleton
class RoomDomainSyncLedger @Inject constructor(
    private val dao: SyncOperationDao
) : DomainSyncLedger {

    override fun beginOrReuseActive(idempotencyKey: String, nowMs: Long): DomainSyncOperation =
        runBlocking {
            val key = SyncWorkCorrelation.boundToken(idempotencyKey)
            dao.findActive(key)?.toDomain()?.let { return@runBlocking it }
            val record = SyncOperationRecord(
                operationId = UUID.randomUUID().toString(),
                idempotencyKey = key,
                state = DomainSyncState.READY.name,
                createdAt = nowMs,
                updatedAt = nowMs
            )
            dao.insertIgnore(record)
            dao.findActive(key)?.toDomain() ?: record.toDomain()
        }

    override fun bindWorkRequest(
        operationId: String,
        workRequestId: String,
        generation: Int,
        nowMs: Long
    ) {
        runBlocking { dao.bindWorkRequest(operationId, workRequestId, generation, nowMs) }
    }

    override fun transition(
        operationId: String,
        state: DomainSyncState,
        nowMs: Long
    ): DomainSyncOperation? = runBlocking {
        dao.updateState(operationId, state.name, nowMs)
        dao.get(operationId)?.toDomain()
    }

    override fun get(operationId: String): DomainSyncOperation? = runBlocking {
        dao.get(operationId)?.toDomain()
    }

    override fun findActive(): DomainSyncOperation? = runBlocking {
        dao.findActive(FieldOpsSyncWorkNames.ACTIVE_IDEMPOTENCY_KEY)?.toDomain()
    }

    override fun snapshot(): List<DomainSyncOperation> = runBlocking {
        dao.all().map { it.toDomain() }
    }

    override fun revertInFlightToReady(nowMs: Long) {
        runBlocking { dao.revertInFlightToReady(nowMs) }
    }
}

private fun SyncOperationRecord.toDomain(): DomainSyncOperation = DomainSyncOperation(
    operationId = operationId,
    idempotencyKey = idempotencyKey,
    state = runCatching { DomainSyncState.valueOf(state) }.getOrDefault(DomainSyncState.READY),
    workRequestId = workRequestId,
    workGeneration = workGeneration,
    createdAt = createdAt,
    updatedAt = updatedAt
)
