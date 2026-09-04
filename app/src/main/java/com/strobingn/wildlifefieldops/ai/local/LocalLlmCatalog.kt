package com.strobingn.wildlifefieldops.ai.local

/**
 * On-device models in LiteRT-LM (.litertlm) format.
 * Files are downloaded to app storage on first use — not committed to git.
 *
 * Stock = official instruct weights.
 * Abliterated = community refusal-reduced conversion of those weights.
 */
data class LocalLlmSpec(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val license: String,
    val notes: String,
    val abliterated: Boolean = false
) {
    val sizeLabel: String
        get() {
            val mb = expectedBytes / (1024.0 * 1024.0)
            return if (mb >= 1000) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
        }
}

object LocalLlmCatalog {
    val QWEN3_06B = LocalLlmSpec(
        id = "qwen3-0.6b",
        displayName = "Qwen3 0.6B (stock)",
        shortLabel = "Qwen3 0.6B",
        fileName = "Qwen3-0.6B.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        expectedBytes = 614_236_160L,
        license = "Apache-2.0",
        notes = "Official unobliterated Qwen3. Fast default for mid-range phones."
    )

    val QWEN35_08B_ABLITERATED = LocalLlmSpec(
        id = "qwen35-0.8b-abliterated",
        displayName = "Qwen3.5 0.8B abliterated",
        shortLabel = "Qwen 0.8B abl",
        fileName = "qwen35_0.8b_abl_mm_q8_ekv4096.litertlm",
        downloadUrl = "https://huggingface.co/g-ntovas/Qwen3.5-0.8B-abliterated-LiteRT/resolve/main/qwen35_0.8b_abl_mm_q8_ekv4096.litertlm",
        expectedBytes = 1_180_000_000L,
        license = "Apache-2.0",
        notes = "Community LiteRT conversion of Qwen3.5-0.8B with refusal direction removed. ~1.1 GB.",
        abliterated = true
    )

    val GEMMA4_E2B = LocalLlmSpec(
        id = "gemma4-e2b",
        displayName = "Gemma 4 E2B (stock)",
        shortLabel = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        expectedBytes = 2_588_147_712L,
        license = "Apache-2.0",
        notes = "Official unobliterated Gemma 4 E2B. Higher quality, ~2.6 GB."
    )

    val GEMMA4_E2B_ABLITERATED = LocalLlmSpec(
        id = "gemma4-e2b-abliterated",
        displayName = "Gemma 4 E2B abliterated",
        shortLabel = "Gemma 4 E2B abl",
        fileName = "Gemma-4-E2B-it-abliterated.litertlm",
        downloadUrl = "https://huggingface.co/nqd145/Gemma-4-E2B-it-abliterated-litertlm/resolve/main/Gemma-4-E2B-it-abliterated.litertlm",
        expectedBytes = 2_500_000_000L,
        license = "Apache-2.0",
        notes = "huihui-ai Gemma 4 E2B abliterated, exported to LiteRT-LM for on-device use.",
        abliterated = true
    )

    val GEMMA4_E2B_UNCENSORED = LocalLlmSpec(
        id = "gemma4-e2b-uncensored",
        displayName = "Gemma 4 E2B Uncensored-MAX",
        shortLabel = "Gemma 4 E2B UNC",
        fileName = "gemma-4-E2B-it-Uncensored-MAX.litertlm",
        downloadUrl = "https://huggingface.co/PeppX/gemma-4-e2b-uncensored-litertlm/resolve/main/gemma-4-E2B-it-Uncensored-MAX.litertlm",
        expectedBytes = 2_550_041_824L,
        license = "Apache-2.0",
        notes = "Community uncensored Gemma 4 E2B LiteRT bundle (INT4, 32K context). 2.37 GB.",
        abliterated = true
    )

    val ALL: List<LocalLlmSpec> = listOf(
        QWEN3_06B,
        QWEN35_08B_ABLITERATED,
        GEMMA4_E2B,
        GEMMA4_E2B_ABLITERATED,
        GEMMA4_E2B_UNCENSORED
    )

    const val DEFAULT_ID: String = "qwen3-0.6b"

    fun byId(id: String?): LocalLlmSpec =
        ALL.firstOrNull { it.id == id } ?: QWEN3_06B
}
