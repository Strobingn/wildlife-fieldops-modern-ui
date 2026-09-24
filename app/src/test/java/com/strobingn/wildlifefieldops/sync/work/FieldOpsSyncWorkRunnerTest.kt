package com.strobingn.wildlifefieldops.sync.work

import com.strobingn.wildlifefieldops.data.repository.SyncResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldOpsSyncWorkRunnerTest {

    @Test
    fun doesNotAckDomainWhenRepositoryFails() = runBlocking {
        val ledger = InMemoryDomainSyncLedger()
        val adapter = WorkAnalyticsAdapter({ true }, RecordingWorkSchedulerTelemetry())
        val gateway = FakeSyncGateway(success = false)
        val runner = FieldOpsSyncWorkRunner(gateway, ledger, adapter)
        val op = ledger.beginOrReuseActive(nowMs = 10L)

        val outcome = runner.run(op.operationId, workRequestId = "wr-fail", generation = 0, nowMs = 20L)

        assertEquals(FieldOpsSyncWorkOutcome.Retry, outcome)
        assertEquals(DomainSyncState.RETRYABLE_FAILURE, ledger.get(op.operationId)?.state)
        assertNotEquals(DomainSyncState.ACKNOWLEDGED, ledger.get(op.operationId)?.state)
        assertEquals(1, gateway.calls)
        assertTrue(adapter.snapshot().any { it.type == SchedulerEventType.RETRY })
        assertTrue(adapter.snapshot().none { it.type == SchedulerEventType.FINISH })
    }

    @Test
    fun doesNotAckWhenRepositoryThrows() = runBlocking {
        val ledger = InMemoryDomainSyncLedger()
        val adapter = WorkAnalyticsAdapter({ true }, RecordingWorkSchedulerTelemetry())
        val gateway = FakeSyncGateway(success = true, throwOnCall = true)
        val runner = FieldOpsSyncWorkRunner(gateway, ledger, adapter)
        val op = ledger.beginOrReuseActive(nowMs = 10L)

        val outcome = runner.run(op.operationId, "wr-boom", 2, nowMs = 11L)

        assertEquals(FieldOpsSyncWorkOutcome.Retry, outcome)
        assertEquals(DomainSyncState.RETRYABLE_FAILURE, ledger.get(op.operationId)?.state)
    }

    @Test
    fun acksOnlyAfterRepositorySuccessAndRecordsWorkIds() = runBlocking {
        val ledger = InMemoryDomainSyncLedger()
        val adapter = WorkAnalyticsAdapter({ true }, RecordingWorkSchedulerTelemetry())
        val gateway = FakeSyncGateway(success = true)
        val runner = FieldOpsSyncWorkRunner(gateway, ledger, adapter)
        val op = ledger.beginOrReuseActive(nowMs = 1L)

        val outcome = runner.run(op.operationId, "wr-ok", 3, nowMs = 2L)

        assertEquals(FieldOpsSyncWorkOutcome.Success, outcome)
        val stored = ledger.get(op.operationId)!!
        assertEquals(DomainSyncState.ACKNOWLEDGED, stored.state)
        assertEquals("wr-ok", stored.workRequestId)
        assertEquals(3, stored.workGeneration)
    }

    @Test
    fun duplicateRunDoesNotDoubleInsertIgnoreDomainEffects() = runBlocking {
        val sink = InsertIgnoreEventSink()
        val ledger = InMemoryDomainSyncLedger()
        val adapter = WorkAnalyticsAdapter({ true }, RecordingWorkSchedulerTelemetry())
        val gateway = FakeSyncGateway(success = true, sink = sink)
        val runner = FieldOpsSyncWorkRunner(gateway, ledger, adapter)
        val first = ledger.beginOrReuseActive(nowMs = 1L)
        val second = ledger.beginOrReuseActive(nowMs = 2L)
        assertEquals(first.operationId, second.operationId)
        assertEquals(1, ledger.snapshot().size)

        runner.run(first.operationId, "wr-1", 0, nowMs = 3L)
        runner.run(first.operationId, "wr-1", 0, nowMs = 4L)

        assertEquals(1, sink.size)
        assertEquals(setOf("event-a"), sink.ids)
        assertEquals(1, ledger.snapshot().size)
        assertEquals(DomainSyncState.ACKNOWLEDGED, ledger.get(first.operationId)?.state)
    }
}

private class InsertIgnoreEventSink {
    val ids = LinkedHashSet<String>()
    fun insertIgnore(id: String): Boolean = ids.add(id)
    val size: Int get() = ids.size
}

private class FakeSyncGateway(
    private val success: Boolean,
    private val throwOnCall: Boolean = false,
    private val sink: InsertIgnoreEventSink? = null
) : FieldOpsSyncGateway {
    var calls: Int = 0

    override fun isCloudConfigured(): Boolean = true

    override suspend fun syncAll(): SyncResult {
        calls += 1
        if (throwOnCall) error("supabase unavailable")
        val inserted = sink?.insertIgnore("event-a") ?: true
        return SyncResult(
            success = success,
            message = if (success) "ok" else "push failed",
            pushedEvents = if (inserted) 1 else 0
        )
    }
}
