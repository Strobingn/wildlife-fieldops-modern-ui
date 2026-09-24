package com.strobingn.wildlifefieldops.sync.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldOpsSyncEnqueueCoordinatorTest {

    @Test
    fun flagOffDoesNotCreateDomainOperationOrTelemetry() {
        val ledger = InMemoryDomainSyncLedger()
        val telemetry = RecordingWorkSchedulerTelemetry()
        val adapter = WorkAnalyticsAdapter({ false }, telemetry)
        val coordinator = FieldOpsSyncEnqueueCoordinator({ false }, ledger, adapter)

        val decision = coordinator.prepare(existingUnfinished = false, nowMs = 5L)

        assertEquals(SyncEnqueueDecision.Disabled, decision)
        assertTrue(ledger.snapshot().isEmpty())
        assertTrue(adapter.snapshot().isEmpty())
    }

    @Test
    fun keepPolicySkipsSecondEnqueueWithoutSecondOperation() {
        val ledger = InMemoryDomainSyncLedger()
        val adapter = WorkAnalyticsAdapter({ true }, RecordingWorkSchedulerTelemetry())
        val coordinator = FieldOpsSyncEnqueueCoordinator({ true }, ledger, adapter)

        val first = coordinator.prepare(existingUnfinished = false, nowMs = 1L)
        val second = coordinator.prepare(existingUnfinished = true, nowMs = 2L)

        val firstReady = first as SyncEnqueueDecision.ReadyToEnqueue
        val secondKept = second as SyncEnqueueDecision.KeptExisting
        assertEquals(firstReady.operation.operationId, secondKept.operation.operationId)
        assertEquals(1, ledger.snapshot().size)
        assertEquals("KEEP", FieldOpsSyncWorkNames.EXISTING_WORK_POLICY)
        assertTrue(FieldOpsSyncWorkNames.shouldSkipBecauseKeep(true))
        assertTrue(secondKept.tags.all(SyncWorkCorrelation::isSafeTag))
    }

    @Test
    fun cancelDoesNotDropReadyDomainState() {
        val ledger = InMemoryDomainSyncLedger()
        val adapter = WorkAnalyticsAdapter({ true }, RecordingWorkSchedulerTelemetry())
        val coordinator = FieldOpsSyncEnqueueCoordinator({ true }, ledger, adapter)
        val prepared = coordinator.prepare(existingUnfinished = false, nowMs = 1L) as SyncEnqueueDecision.ReadyToEnqueue
        ledger.transition(prepared.operation.operationId, DomainSyncState.UPLOADING, nowMs = 2L)

        coordinator.onWorkCancelled()

        assertEquals(DomainSyncState.READY, ledger.get(prepared.operation.operationId)?.state)
        assertEquals(1, ledger.snapshot().size)
    }
}
