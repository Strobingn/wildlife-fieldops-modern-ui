package com.strobingn.wildlifefieldops.sync.work

/**
 * Production-facing wrapper around WorkManager 2.12 experimental analytics.
 *
 * Callers record [SchedulerEvent]s through this type. When the canary flag is
 * off, every write is a no-op and [shouldRegisterExperimentalListeners] is
 * false so [WorkManagerConfigurationFactory] never touches
 * `androidx.work.analytics` / `ExperimentalEventsApi`.
 *
 * WorkManager metrics default to **~7 days** of retention
 * ([androidx.work.analytics.WorkMetricsInfoRepository.DEFAULT_RETENTION_TIME_MILLIS]).
 * Domain ACK / conflict / retry lives in Room via [DomainSyncLedger] and is
 * not pruned with WM metrics.
 */
class WorkAnalyticsAdapter(
    private val flag: WorkManagerSyncCanaryFlag,
    private val telemetry: WorkSchedulerTelemetry
) {
    fun isFlagEnabled(): Boolean = flag.isEnabled()

    fun shouldRegisterExperimentalListeners(): Boolean = flag.isEnabled()

    fun record(event: SchedulerEvent) {
        if (!flag.isEnabled()) return
        telemetry.record(event)
    }

    fun snapshot(): List<SchedulerEvent> =
        if (flag.isEnabled()) telemetry.snapshot() else emptyList()
}
