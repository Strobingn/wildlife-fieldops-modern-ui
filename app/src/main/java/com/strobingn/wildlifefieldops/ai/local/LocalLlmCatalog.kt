package com.strobingn.wildlifefieldops.ai.local

/**
 * On-device models in LiteRT-LM (.litertlm) format.
 * Files are downloaded to app storage on first use — not committed to git.
 */
data class LocalLlmSpec(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val license: String,
    val notes: String
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
        displayName = "Qwen3 0.6B",
        shortLabel = "Qwen3 0.6B",
        fileName = "Qwen3-0.6B.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        expectedBytes = 614_236_160L,
        license = "Apache-2.0",
        notes = "Fast default. Works on mid-range phones. ~9–21 tok/s."
    )

    val GEMMA4_E2B = LocalLlmSpec(
        id = "gemma4-e2b",
        displayName = "Gemma 4 E2B",
        shortLabel = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        expectedBytes = 2_588_147_712L,
        license = "Apache-2.0",
        notes = "Higher quality. Needs ~3 GB free storage and a recent flagship/mid phone."
    )

    val ALL: List<LocalLlmSpec> = listOf(QWEN3_06B, GEMMA4_E2B)

    const val DEFAULT_ID: String = "qwen3-0.6b"

    fun byId(id: String?): LocalLlmSpec =
        ALL.firstOrNull { it.id == id } ?: QWEN3_06B
}
