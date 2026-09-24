package com.strobingn.wildlifefieldops.sync.work

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Scheduler / execution telemetry (ledger 2). Separate from [DomainSyncLedger].
 * Values here are correlation IDs, generation, and durations only.
 */
enum class SchedulerEventType {
    ENQUEUE,
    UNBLOCK,
    START,
    FINISH,
    STOP,
    RETRY,
    FAILED_PREREQUISITE
}

data class SchedulerEvent(
    val type: SchedulerEventType,
    val workRequestId: String? = null,
    val generation: Int? = null,
    val durationMs: Long? = null,
    val tags: Set<String> = emptySet(),
    val timestampMs: Long = System.currentTimeMillis()
)

interface WorkSchedulerTelemetry {
    fun record(event: SchedulerEvent)
    fun snapshot(): List<SchedulerEvent>
}

class NoOpWorkSchedulerTelemetry : WorkSchedulerTelemetry {
    override fun record(event: SchedulerEvent) = Unit
    override fun snapshot(): List<SchedulerEvent> = emptyList()
}

class RecordingWorkSchedulerTelemetry : WorkSchedulerTelemetry {
    private val events = CopyOnWriteArrayList<SchedulerEvent>()

    override fun record(event: SchedulerEvent) {
        events += event.copy(tags = SyncWorkCorrelation.sanitizeTags(event.tags))
    }

    override fun snapshot(): List<SchedulerEvent> = events.toList()
}
