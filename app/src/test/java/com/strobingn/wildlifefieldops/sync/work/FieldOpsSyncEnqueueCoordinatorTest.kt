package com.strobingn.wildlifefieldops.sync.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldOpsSyncEnqueueCoordinatorTest {

    @Test
    fun gateOffDoesNotCreateDomainOperationOrTelemetry() {
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
    fun autoSyncEnqueuesEvenWhenWorkAlreadyUnfinished() {
        val ledger = InMemoryDomainSyncLedger()
        val adapter = WorkAnalyticsAdapter({ true }, RecordingWorkSchedulerTelemetry())
        val coordinator = FieldOpsSyncEnqueueCoordinator({ true }, ledger, adapter)

        val first = coordinator.prepare(existingUnfinished = false, nowMs = 1L)
        val second = coordinator.prepare(existingUnfinished = true, nowMs = 2L)

        assertTrue(first is SyncEnqueueDecision.ReadyToEnqueue)
        assertTrue(second is SyncEnqueueDecision.ReadyToEnqueue)
        assertEquals("APPEND_OR_REPLACE", FieldOpsSyncWorkNames.EXISTING_WORK_POLICY)
        assertEquals(
            (first as SyncEnqueueDecision.ReadyToEnqueue).operation.operationId,
            (second as SyncEnqueueDecision.ReadyToEnqueue).operation.operationId
        )
        assertTrue(second.tags.all(SyncWorkCorrelation::isSafeTag))
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
