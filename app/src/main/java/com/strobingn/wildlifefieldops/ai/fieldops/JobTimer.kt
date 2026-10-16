package com.strobingn.wildlifefieldops.ai.fieldops

data class JobTimerState(
    val startedAt: Long? = null,
    val elapsedMs: Long = 0L
) {
    val running: Boolean get() = startedAt != null

    fun displayedMs(now: Long = System.currentTimeMillis()): Long {
        val live = startedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        return elapsedMs + live
    }

    fun displayedMinutes(now: Long = System.currentTimeMillis()): Long = displayedMs(now) / 60_000L
}

object JobTimer {
    fun start(state: JobTimerState, now: Long = System.currentTimeMillis()): JobTimerState {
        if (state.running) return state
        return state.copy(startedAt = now)
    }

    fun stop(state: JobTimerState, now: Long = System.currentTimeMillis()): JobTimerState {
        val started = state.startedAt ?: return state
        return JobTimerState(startedAt = null, elapsedMs = state.elapsedMs + (now - started).coerceAtLeast(0L))
    }

    fun withTypedMinutes(state: JobTimerState, minutes: Long): JobTimerState =
        state.copy(elapsedMs = minutes.coerceAtLeast(0L) * 60_000L)

    fun format(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0L)
        val hours = totalMin / 60
        val minutes = totalMin % 60
        return String.format(java.util.Locale.US, "%d:%02d", hours, minutes)
    }
}
