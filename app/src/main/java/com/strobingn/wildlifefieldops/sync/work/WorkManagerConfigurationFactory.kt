package com.strobingn.wildlifefieldops.sync.work

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExperimentalEventsApi
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.analytics.ExperimentalWorkMetricsApi
import androidx.work.analytics.WorkMetricsInfoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds [Configuration] and, when the canary is on, registers experimental
 * schedule / execution listeners plus [WorkMetricsInfoRepository].
 *
 * Production call sites (scheduler, Settings, runner) must not import
 * `androidx.work.analytics` — only this factory does.
 */
@Singleton
class WorkManagerConfigurationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adapter: WorkAnalyticsAdapter
) {
    fun create(): Configuration {
        val builder = Configuration.Builder()
        if (adapter.shouldRegisterExperimentalListeners()) {
            attachExperimental(builder)
        }
        return builder.build()
    }

    @OptIn(ExperimentalEventsApi::class, ExperimentalWorkMetricsApi::class)
    private fun attachExperimental(builder: Configuration.Builder) {
        val metrics = WorkMetricsInfoRepository(context)
        val bridge = ExperimentalWorkListenerBridge(adapter, metrics)
        builder.setScheduleEventListener(bridge)
        builder.setExecutionEventListener(bridge)
        Log.i(TAG, "WM canary listeners attached (metrics retention ~7 days)")
    }

    companion object {
        private const val TAG = "FieldOpsWmCanary"
    }
}

/**
 * Internal only: maps experimental WM hooks to [WorkAnalyticsAdapter] and
 * forwards the same events to [WorkMetricsInfoRepository].
 */
@OptIn(ExperimentalEventsApi::class, ExperimentalWorkMetricsApi::class)
internal class ExperimentalWorkListenerBridge(
    private val adapter: WorkAnalyticsAdapter,
    private val metrics: WorkMetricsInfoRepository
) : androidx.work.ScheduleEventListener, androidx.work.ExecutionEventListener {

    override suspend fun onEnqueued(workInfo: WorkInfo) {
        metrics.onEnqueued(workInfo)
        adapter.record(workInfo.toSchedulerEvent(SchedulerEventType.ENQUEUE))
    }

    override suspend fun onUnblocked(workInfo: WorkInfo) {
        metrics.onUnblocked(workInfo)
        adapter.record(workInfo.toSchedulerEvent(SchedulerEventType.UNBLOCK))
    }

    override suspend fun onPrerequisiteFailed(workInfo: WorkInfo) {
        metrics.onPrerequisiteFailed(workInfo)
        adapter.record(workInfo.toSchedulerEvent(SchedulerEventType.FAILED_PREREQUISITE))
    }

    override suspend fun onCancelled(workInfo: WorkInfo) {
        metrics.onCancelled(workInfo)
        adapter.record(workInfo.toSchedulerEvent(SchedulerEventType.STOP))
    }

    override suspend fun onUpdated(oldWorkInfo: WorkInfo, updatedWorkInfo: WorkInfo) {
        metrics.onUpdated(oldWorkInfo, updatedWorkInfo)
        adapter.record(updatedWorkInfo.toSchedulerEvent(SchedulerEventType.ENQUEUE))
    }

    override suspend fun onStarted(workInfo: WorkInfo) {
        metrics.onStarted(workInfo)
        adapter.record(workInfo.toSchedulerEvent(SchedulerEventType.START))
    }

    override suspend fun onFinished(result: ListenableWorker.Result, workInfo: WorkInfo) {
        metrics.onFinished(result, workInfo)
        val type = if (result is ListenableWorker.Result.Retry) {
            SchedulerEventType.RETRY
        } else {
            SchedulerEventType.FINISH
        }
        adapter.record(workInfo.toSchedulerEvent(type))
    }

    override suspend fun onStopped(stopReason: Int, workInfo: WorkInfo) {
        metrics.onStopped(stopReason, workInfo)
        adapter.record(workInfo.toSchedulerEvent(SchedulerEventType.STOP))
    }

    override suspend fun onException(throwable: Throwable, workInfo: WorkInfo) {
        metrics.onException(throwable, workInfo)
        adapter.record(workInfo.toSchedulerEvent(SchedulerEventType.RETRY))
    }
}

private fun WorkInfo.toSchedulerEvent(type: SchedulerEventType): SchedulerEvent =
    SchedulerEvent(
        type = type,
        workRequestId = id.toString(),
        generation = generation,
        durationMs = null,
        tags = SyncWorkCorrelation.sanitizeTags(tags)
    )
