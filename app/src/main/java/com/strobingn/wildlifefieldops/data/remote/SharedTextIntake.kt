package com.strobingn.wildlifefieldops.data.remote

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SharedText(
    val id: Long,
    val body: String,
    val senderPhone: String?
)

/**
 * Plain-text share payload, separated from [Intent] so tests can cover
 * single and multi-message shares without a device.
 */
data class SharePayload(
    val action: String?,
    val mimeType: String?,
    val text: String? = null,
    val textList: List<String> = emptyList(),
    val clipTexts: List<String> = emptyList(),
    val subject: String? = null,
    val extraPhones: List<String> = emptyList()
)

object SharedTextIntake {
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"

    fun fromIntent(intent: Intent?): SharedText? {
        if (intent == null) return null
        val list = intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
            ?.map { it.toString() }
            .orEmpty()
        val single = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val clips = buildList {
            val clip = intent.clipData ?: return@buildList
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.text?.toString()?.let { add(it) }
            }
        }
        val phones = PHONE_EXTRA_KEYS.mapNotNull { key ->
            intent.getStringExtra(key)?.takeIf { it.isNotBlank() }
        }
        val parsed = extract(
            SharePayload(
                action = intent.action,
                mimeType = intent.type,
                text = single,
                textList = list,
                clipTexts = clips,
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                extraPhones = phones
            )
        ) ?: return null
        return parsed.copy(id = System.nanoTime())
    }

    fun extract(payload: SharePayload): SharedText? {
        val action = payload.action ?: return null
        if (action != ACTION_SEND && action != ACTION_SEND_MULTIPLE) return null
        val mime = payload.mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (mime.isNotBlank() && mime != "text/plain" && !mime.startsWith("text/")) return null

        val parts = LinkedHashSet<String>()
        if (action == ACTION_SEND_MULTIPLE && payload.textList.isNotEmpty()) {
            payload.textList.forEach { part ->
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) parts += trimmed
            }
        } else if (!payload.text.isNullOrBlank()) {
            parts += payload.text.trim()
        }
        payload.clipTexts.forEach { clip ->
            val trimmed = clip.trim()
            if (trimmed.isNotEmpty()) parts += trimmed
        }
        val subject = payload.subject?.trim().orEmpty()
        val subjectPhone = payload.extraPhones.firstOrNull { TextMessageImport.looksLikePhone(it) }
            ?: TextMessageImport.formatPhone(subject).takeIf { it.isNotBlank() }
        val includeSubject = subject.isNotBlank() && (
            subjectPhone != null || subject.contains("from", ignoreCase = true)
            )
        val bodyParts = buildList {
            if (includeSubject) add(subject)
            addAll(parts)
        }
        val body = TextMessageImport.joinBodies(bodyParts)
        if (body.isBlank()) return null
        return SharedText(id = 0L, body = body, senderPhone = subjectPhone)
    }

    private val PHONE_EXTRA_KEYS = listOf(
        "address",
        "phone",
        "phone_number",
        "android.intent.extra.PHONE_NUMBER",
        "sms_address",
        "originating_address"
    )
}

/**
 * Holds a share until the New Job form reads it. Nothing is written to Room here.
 */
object TextShareInbox {
    private val pendingState = MutableStateFlow<SharedText?>(null)
    val pending: StateFlow<SharedText?> = pendingState.asStateFlow()

    fun offer(text: SharedText) {
        pendingState.value = text
    }

    fun peek(): SharedText? = pendingState.value

    fun consume(id: Long) {
        if (pendingState.value?.id == id) pendingState.value = null
    }
}
