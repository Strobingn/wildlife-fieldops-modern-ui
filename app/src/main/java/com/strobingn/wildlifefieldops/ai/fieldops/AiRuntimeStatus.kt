package com.strobingn.wildlifefieldops.ai.fieldops

/**
 * Which brain will (or did) draft field copy. Heuristic always works offline.
 */
enum class AiRuntimeMode {
    CLOUD,
    ON_DEVICE,
    HEURISTIC
}

data class AiRuntimeStatus(
    val mode: AiRuntimeMode,
    val label: String,
    val detail: String
) {
    companion object {
        fun resolve(
            cloudConfigured: Boolean,
            onDeviceReady: Boolean,
            lastUsed: String = ""
        ): AiRuntimeStatus {
            val preferred = when {
                cloudConfigured -> AiRuntimeMode.CLOUD
                onDeviceReady -> AiRuntimeMode.ON_DEVICE
                else -> AiRuntimeMode.HEURISTIC
            }
            val used = parse(lastUsed) ?: preferred
            return of(used, cloudConfigured, onDeviceReady)
        }

        fun of(
            mode: AiRuntimeMode,
            cloudConfigured: Boolean = false,
            onDeviceReady: Boolean = false
        ): AiRuntimeStatus = when (mode) {
            AiRuntimeMode.CLOUD -> AiRuntimeStatus(
                mode = mode,
                label = "Cloud AI",
                detail = "Grok / cloud draft. Fields stay editable."
            )
            AiRuntimeMode.ON_DEVICE -> AiRuntimeStatus(
                mode = mode,
                label = "On-device AI",
                detail = if (onDeviceReady) "Local LLM draft. Works without signal."
                else "On-device model not downloaded — using heuristics."
            )
            AiRuntimeMode.HEURISTIC -> AiRuntimeStatus(
                mode = mode,
                label = "Offline heuristic",
                detail = buildString {
                    append("Cloud AI unavailable")
                    if (!cloudConfigured && !onDeviceReady) append(" and no local model")
                    append(". Catalog draft only — edit before you quote or file.")
                }
            )
        }

        fun parse(raw: String): AiRuntimeMode? = when (raw.trim().lowercase()) {
            "cloud", "grok", "llm" -> AiRuntimeMode.CLOUD
            "on_device", "on-device", "local", "local_llm" -> AiRuntimeMode.ON_DEVICE
            "heuristic", "template", "offline_ml", "offline" -> AiRuntimeMode.HEURISTIC
            else -> null
        }

        fun wireName(mode: AiRuntimeMode): String = when (mode) {
            AiRuntimeMode.CLOUD -> "cloud"
            AiRuntimeMode.ON_DEVICE -> "on_device"
            AiRuntimeMode.HEURISTIC -> "heuristic"
        }
    }
}
