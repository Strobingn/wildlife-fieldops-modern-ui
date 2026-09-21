package com.strobingn.wildlifefieldops.ai.asr

/**
 * Backend allowlist policy for ASR inference.
 *
 * ## Policy key
 * The allowlist key is the 5-tuple:
 * ```
 * model_sha256 × runtime_version × backend × device_key × window_config
 * ```
 * Any (model × runtime × device × window) combination that is not present in
 * the allowlist is treated as **untested** and defaults to CPU-only.
 * This is the fail-closed default: untested accelerator combinations are never
 * selected automatically.
 *
 * See docs/adr/0003-asr-backend-policy.md for full rationale.
 */
interface BackendPolicy {

    /**
     * Resolve the ordered list of [InferenceBackend]s to attempt for the given
     * policy key.
     *
     * Callers must iterate the returned list in order, stopping at the first
     * attempt that either succeeds or exhausts the CPU fallback budget
     * (see [VoiceCaptureOrchestrator]).
     *
     * Guarantees:
     *  - The returned list is never empty.
     *  - [InferenceBackend.CPU] is always present as the last element.
     *
     * @param modelSha256    SHA-256 of the ASR model flatbuffer.
     * @param runtimeVersion Runtime version string (e.g. "litert-lm-1.0.1").
     * @param deviceKey      Device/SoC identifier (e.g. "google/Pixel_8_Pro/tensor_g3").
     * @param windowConfig   Window configuration string.
     * @return Non-empty ordered list; most preferred backend first.
     */
    fun resolve(
        modelSha256: String,
        runtimeVersion: String,
        deviceKey: String,
        windowConfig: String,
    ): List<InferenceBackend>

    companion object {
        /** Default production implementation with an empty allowlist (CPU-only safe default). */
        val Default: BackendPolicy = DefaultBackendPolicy()
    }
}

/**
 * An allowlisted entry for a specific (model × runtime × device × window) tuple.
 *
 * @param approvedBackends Set of [InferenceBackend]s approved for this combination.
 *                         CPU is implicitly always available and need not be listed,
 *                         but may be included explicitly.
 */
data class AllowlistEntry(
    val modelSha256: String,
    val runtimeVersion: String,
    val deviceKey: String,
    val windowConfig: String,
    val approvedBackends: Set<InferenceBackend>,
)

/**
 * Default production [BackendPolicy] backed by a static allowlist.
 *
 * The allowlist ships empty; production systems should populate it from a
 * signed remote config fetched at startup, with a local signed bundle as fallback.
 *
 * TODO: replace [allowlist] with signed remote config + local fallback bundle.
 *
 * Resolution order within an approved set: NPU > GPU > CPU.
 * CPU is always appended even if not listed in [AllowlistEntry.approvedBackends].
 */
internal class DefaultBackendPolicy(
    private val allowlist: List<AllowlistEntry> = emptyList(),
) : BackendPolicy {

    private val preferenceOrder = listOf(InferenceBackend.NPU, InferenceBackend.GPU, InferenceBackend.CPU)

    override fun resolve(
        modelSha256: String,
        runtimeVersion: String,
        deviceKey: String,
        windowConfig: String,
    ): List<InferenceBackend> {
        val entry = allowlist.firstOrNull {
            it.modelSha256 == modelSha256 &&
                it.runtimeVersion == runtimeVersion &&
                it.deviceKey == deviceKey &&
                it.windowConfig == windowConfig
        }

        return if (entry == null) {
            // Untested combination → CPU only (fail-closed safe default).
            listOf(InferenceBackend.CPU)
        } else {
            // Return approved backends in canonical preference order.
            // CPU is always appended as the guaranteed final fallback.
            val ordered = preferenceOrder.filter { it in entry.approvedBackends }
            if (InferenceBackend.CPU in ordered) ordered else ordered + InferenceBackend.CPU
        }
    }
}
