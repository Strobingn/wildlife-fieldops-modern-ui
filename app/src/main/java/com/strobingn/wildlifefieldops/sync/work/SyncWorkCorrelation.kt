package com.strobingn.wildlifefieldops.sync.work

/**
 * Non-sensitive correlation tokens for WorkRequest tags and input [androidx.work.Data].
 *
 * Allowed: operation / idempotency IDs, the unique-work name, and the `fo-sync` class tag.
 * Never encode photos, GPS, species notes, tokens, or other PII.
 */
object SyncWorkCorrelation {
    const val KEY_OPERATION_ID = "operationId"
    const val KEY_IDEMPOTENCY_KEY = "idempotencyKey"

    const val TAG_OPERATION_PREFIX = "fo-op:"
    const val TAG_IDEMPOTENCY_PREFIX = "fo-idk:"

    const val MAX_TOKEN_LEN = 64
    const val MAX_TAG_LEN = 80

    private val TOKEN_CHARS = Regex("[^A-Za-z0-9._-]")

    fun boundToken(raw: String): String =
        TOKEN_CHARS.replace(raw.trim(), "-")
            .trim('-')
            .ifBlank { "unknown" }
            .take(MAX_TOKEN_LEN)

    fun operationTag(operationId: String): String =
        (TAG_OPERATION_PREFIX + boundToken(operationId)).take(MAX_TAG_LEN)

    fun idempotencyTag(idempotencyKey: String): String =
        (TAG_IDEMPOTENCY_PREFIX + boundToken(idempotencyKey)).take(MAX_TAG_LEN)

    fun tagsFor(operation: DomainSyncOperation): Set<String> = linkedSetOf(
        FieldOpsSyncWorkNames.UNIQUE_WORK_NAME,
        FieldOpsSyncWorkNames.WORKER_CLASS_TAG,
        operationTag(operation.operationId),
        idempotencyTag(operation.idempotencyKey)
    )

    fun inputFor(operation: DomainSyncOperation): Map<String, String> = mapOf(
        KEY_OPERATION_ID to boundToken(operation.operationId),
        KEY_IDEMPOTENCY_KEY to boundToken(operation.idempotencyKey)
    )

    fun isSafeTag(tag: String): Boolean {
        if (tag.length > MAX_TAG_LEN) return false
        return tag == FieldOpsSyncWorkNames.UNIQUE_WORK_NAME ||
            tag == FieldOpsSyncWorkNames.WORKER_CLASS_TAG ||
            tag.startsWith(TAG_OPERATION_PREFIX) ||
            tag.startsWith(TAG_IDEMPOTENCY_PREFIX)
    }

    fun sanitizeTags(tags: Iterable<String>): Set<String> =
        tags.filter(::isSafeTag).toCollection(LinkedHashSet())
}
