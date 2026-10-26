package com.strobingn.wildlifefieldops.ai.admission

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Context-to-memory coexistence budget for on-device llama.cpp field tasks
 * (brief 2026-10-21). Explanation faithfulness stays in [ExplanationAdmissionGate];
 * this gate decides whether a context may be initialized or expanded.
 *
 * Field tasks are capped at [FIELD_TASK_CONTEXT_CAP] tokens. Admission requires
 * the projected free memory, after KV cache + model weights + active subsystem
 * overhead, to leave at least [MIN_HEADROOM_FRACTION] of system RAM.
 */
enum class FieldSubsystem {
    CAMERAX,
    ARCORE,
    MAPS,
}

enum class ContextAdmissionAction {
    INITIALIZE,
    EXPAND,
    HOLD,
    DENY,
}

data class MemorySnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
)

data class ContextBudgetConfig(
    val maxContextTokens: Int = ContextMemoryAdmission.FIELD_TASK_CONTEXT_CAP,
    val minContextTokens: Int = ContextMemoryAdmission.MIN_CONTEXT_TOKENS,
    val minHeadroomFraction: Double = ContextMemoryAdmission.MIN_HEADROOM_FRACTION,
    /** Qwen2.5-3B-class GQA geometry used to estimate FP16 KV bytes. */
    val layers: Int = 36,
    val kvHeads: Int = 2,
    val headDim: Int = 128,
    val bytesPerElement: Int = 2,
    val runtimeOverheadBytes: Long = 48L * 1024L * 1024L,
    val subsystemOverheadBytes: Map<FieldSubsystem, Long> = mapOf(
        FieldSubsystem.CAMERAX to 96L * 1024L * 1024L,
        FieldSubsystem.ARCORE to 192L * 1024L * 1024L,
        FieldSubsystem.MAPS to 128L * 1024L * 1024L,
    ),
) {
    init {
        require(minContextTokens >= ContextMemoryAdmission.TOKEN_STEP) {
            "minContextTokens must be at least one token step"
        }
        require(maxContextTokens in minContextTokens..ContextMemoryAdmission.FIELD_TASK_CONTEXT_CAP) {
            "field-task maxContextTokens must be <= ${ContextMemoryAdmission.FIELD_TASK_CONTEXT_CAP}"
        }
        require(minHeadroomFraction + 1e-9 >= ContextMemoryAdmission.MIN_HEADROOM_FRACTION) {
            "headroom floor cannot be weakened below 20%"
        }
    }

    val bytesPerToken: Long
        get() = layers.toLong() * kvHeads.toLong() * headDim.toLong() * 2L * bytesPerElement.toLong()
}

data class ContextAdmissionDecision(
    val action: ContextAdmissionAction,
    val grantedContextTokens: Int,
    val requestedContextTokens: Int,
    val estimatedBytes: Long,
    val headroomFraction: Double,
    val reasons: List<String>,
) {
    val admitted: Boolean
        get() = action != ContextAdmissionAction.DENY && grantedContextTokens > 0
}

/**
 * Structured job / observation state. [chatHistory] is accepted so callers can
 * pass a transcript in, and is never copied into the prompt.
 */
data class StructuredFieldState(
    val jobId: String = "",
    val customer: String = "",
    val address: String = "",
    val species: String = "",
    val serviceType: String = "",
    val priority: String = "",
    val damage: List<String> = emptyList(),
    val entries: List<String> = emptyList(),
    val notes: String = "",
    val observationSummary: String = "",
    val recentJobTypes: List<String> = emptyList(),
    val chatHistory: List<String> = emptyList(),
)

/**
 * Reference-counted view of subsystems that are resident with the LLM.
 * Camera, AR, and Maps call [acquire] / [release] around their sessions.
 */
object FieldSubsystemPresence {
    private val counts = ConcurrentHashMap<FieldSubsystem, AtomicInteger>()

    fun acquire(subsystem: FieldSubsystem) {
        counts.computeIfAbsent(subsystem) { AtomicInteger() }.incrementAndGet()
    }

    fun release(subsystem: FieldSubsystem) {
        val count = counts[subsystem] ?: return
        while (true) {
            val current = count.get()
            if (current <= 0) return
            if (count.compareAndSet(current, current - 1)) return
        }
    }

    fun active(): Set<FieldSubsystem> =
        counts.filterValues { it.get() > 0 }.keys.toSet()

    internal fun resetForTests() {
        counts.clear()
    }
}

object ContextMemoryAdmission {
    const val FIELD_TASK_CONTEXT_CAP = 2048
    const val MIN_CONTEXT_TOKENS = 256
    const val MIN_HEADROOM_FRACTION = 0.20
    const val TOKEN_STEP = 128

    fun evaluate(
        requestedContextTokens: Int,
        residentContextTokens: Int,
        memory: MemorySnapshot,
        modelWeightBytes: Long,
        modelWeightsResident: Boolean,
        activeSubsystems: Set<FieldSubsystem> = emptySet(),
        config: ContextBudgetConfig = ContextBudgetConfig(),
    ): ContextAdmissionDecision {
        val reasons = mutableListOf<String>()
        val requested = requestedContextTokens.coerceAtLeast(0)
        val cap = config.maxContextTokens
        val capped = requested.coerceAtMost(cap)
        if (requested > cap) {
            reasons += "clamped requested $requested to field-task cap $cap"
        }
        if (memory.totalBytes <= 0L || memory.availableBytes < 0L) {
            return deny(requested, reasons + "system memory snapshot is incomplete")
        }

        val resident = if (modelWeightsResident) residentContextTokens.coerceAtLeast(0) else 0
        val weightsResident = modelWeightsResident && resident > 0
        val target = largestFit(
            capped = capped,
            memory = memory,
            modelWeightBytes = modelWeightBytes.coerceAtLeast(0L),
            weightsResident = weightsResident,
            residentTokens = resident,
            activeSubsystems = activeSubsystems,
            config = config,
        )

        if (weightsResident) {
            val holding = headroomFor(
                tokens = resident,
                memory = memory,
                modelWeightBytes = modelWeightBytes,
                weightsResident = true,
                residentTokens = resident,
                activeSubsystems = activeSubsystems,
                config = config,
            )
            if (target == null || target <= resident) {
                reasons += "holding resident context $resident; not expanding to $capped"
                return ContextAdmissionDecision(
                    action = ContextAdmissionAction.HOLD,
                    grantedContextTokens = resident,
                    requestedContextTokens = requested,
                    estimatedBytes = holding.second,
                    headroomFraction = holding.first,
                    reasons = reasons,
                )
            }
            val expanded = headroomFor(
                tokens = target,
                memory = memory,
                modelWeightBytes = modelWeightBytes,
                weightsResident = true,
                residentTokens = resident,
                activeSubsystems = activeSubsystems,
                config = config,
            )
            reasons += "expanding context $resident -> $target at ${pct(expanded.first)} headroom"
            return ContextAdmissionDecision(
                action = ContextAdmissionAction.EXPAND,
                grantedContextTokens = target,
                requestedContextTokens = requested,
                estimatedBytes = expanded.second,
                headroomFraction = expanded.first,
                reasons = reasons,
            )
        }

        if (target != null) {
            val admitted = headroomFor(
                tokens = target,
                memory = memory,
                modelWeightBytes = modelWeightBytes,
                weightsResident = false,
                residentTokens = 0,
                activeSubsystems = activeSubsystems,
                config = config,
            )
            reasons += "initializing context $target at ${pct(admitted.first)} headroom"
            return ContextAdmissionDecision(
                action = ContextAdmissionAction.INITIALIZE,
                grantedContextTokens = target,
                requestedContextTokens = requested,
                estimatedBytes = admitted.second,
                headroomFraction = admitted.first,
                reasons = reasons,
            )
        }

        val blocked = headroomFor(
            tokens = config.minContextTokens,
            memory = memory,
            modelWeightBytes = modelWeightBytes,
            weightsResident = false,
            residentTokens = 0,
            activeSubsystems = activeSubsystems,
            config = config,
        )
        reasons += "refusing context init; headroom ${pct(blocked.first)} is below " +
            "${pct(config.minHeadroomFraction)}"
        return ContextAdmissionDecision(
            action = ContextAdmissionAction.DENY,
            grantedContextTokens = 0,
            requestedContextTokens = requested,
            estimatedBytes = blocked.second,
            headroomFraction = blocked.first,
            reasons = reasons,
        )
    }

    /**
     * Rebuilds a bounded field prompt from structured state.
     * Multi-turn [StructuredFieldState.chatHistory] is dropped.
     */
    fun compact(
        state: StructuredFieldState,
        maxChars: Int = 1600,
        notesLimit: Int = 280,
    ): String {
        require(maxChars >= 64) { "maxChars must stay bounded and usable" }
        fun clip(raw: String, limit: Int): String =
            raw.trim().replace(Regex("\\s+"), " ").take(limit)

        val lines = buildList {
            add("FIELD_TASK")
            if (state.jobId.isNotBlank()) add("job: ${clip(state.jobId, 64)}")
            if (state.customer.isNotBlank()) add("customer: ${clip(state.customer, 80)}")
            if (state.address.isNotBlank()) add("address: ${clip(state.address, 120)}")
            if (state.species.isNotBlank()) add("species: ${clip(state.species, 120)}")
            if (state.serviceType.isNotBlank()) add("service: ${clip(state.serviceType, 80)}")
            if (state.priority.isNotBlank()) add("priority: ${clip(state.priority, 24)}")
            if (state.damage.isNotEmpty()) {
                add("damage: " + state.damage.take(6).joinToString(", ") { clip(it, 40) })
            }
            if (state.entries.isNotEmpty()) {
                add("entries: " + state.entries.take(6).joinToString(", ") { clip(it, 40) })
            }
            if (state.observationSummary.isNotBlank()) {
                add("observation: ${clip(state.observationSummary, notesLimit)}")
            }
            if (state.notes.isNotBlank()) add("notes: ${clip(state.notes, notesLimit)}")
            if (state.recentJobTypes.isNotEmpty()) {
                add("recent_types: " + state.recentJobTypes.take(5).joinToString(", ") { clip(it, 32) })
            }
        }
        val text = lines.joinToString("\n")
        return if (text.length <= maxChars) text else text.take(maxChars)
    }

    internal fun kvBytes(tokens: Int, config: ContextBudgetConfig): Long =
        tokens.coerceAtLeast(0).toLong() * config.bytesPerToken

    private fun largestFit(
        capped: Int,
        memory: MemorySnapshot,
        modelWeightBytes: Long,
        weightsResident: Boolean,
        residentTokens: Int,
        activeSubsystems: Set<FieldSubsystem>,
        config: ContextBudgetConfig,
    ): Int? {
        if (capped < config.minContextTokens) return null
        var tokens = capped - (capped % TOKEN_STEP)
        if (tokens < config.minContextTokens) return null
        while (tokens >= config.minContextTokens) {
            val fraction = headroomFor(
                tokens = tokens,
                memory = memory,
                modelWeightBytes = modelWeightBytes,
                weightsResident = weightsResident,
                residentTokens = residentTokens,
                activeSubsystems = activeSubsystems,
                config = config,
            ).first
            if (fraction + 1e-12 >= config.minHeadroomFraction) return tokens
            tokens -= TOKEN_STEP
        }
        return null
    }

    private fun headroomFor(
        tokens: Int,
        memory: MemorySnapshot,
        modelWeightBytes: Long,
        weightsResident: Boolean,
        residentTokens: Int,
        activeSubsystems: Set<FieldSubsystem>,
        config: ContextBudgetConfig,
    ): Pair<Double, Long> {
        val subsystemBytes = activeSubsystems.sumOf { config.subsystemOverheadBytes[it] ?: 0L }
        val weightCharge = if (weightsResident) 0L else modelWeightBytes.coerceAtLeast(0L)
        val freedKv = if (weightsResident) kvBytes(residentTokens, config) else 0L
        val kv = kvBytes(tokens, config)
        val estimated = weightCharge + kv + subsystemBytes + config.runtimeOverheadBytes
        val projectedFree = memory.availableBytes + freedKv - estimated
        val fraction = projectedFree.toDouble() / memory.totalBytes.toDouble()
        return fraction to estimated
    }

    private fun deny(requested: Int, reasons: List<String>): ContextAdmissionDecision =
        ContextAdmissionDecision(
            action = ContextAdmissionAction.DENY,
            grantedContextTokens = 0,
            requestedContextTokens = requested,
            estimatedBytes = 0L,
            headroomFraction = 0.0,
            reasons = reasons,
        )

    private fun pct(fraction: Double): String =
        String.format(Locale.US, "%.1f%%", fraction * 100.0)
}
