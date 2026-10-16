package com.strobingn.wildlifefieldops.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the Import from text screen sends the text it reads. */
enum class TextImportTarget(val key: String) {
    /** New Job form. Same handoff as the Android share sheet. */
    JOB("job"),

    /** New Customer form. */
    CUSTOMER("customer");

    companion object {
        fun from(raw: String?): TextImportTarget =
            values().firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: JOB
    }
}

/**
 * Text read on the Import from text screen for the New Customer form.
 * Jobs use [TextShareInbox], which the share sheet already feeds.
 */
object CustomerTextInbox {
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

/**
 * Rules for the in-app Import from text screen. Parsing stays in
 * [TextMessageImport]; this only decides what goes in the box and where
 * the text is handed off.
 */
object TextImportEntry {
    const val ACTION_LABEL = "Import from text"
    const val READ_LABEL = "Read text"
    const val PASTE_LABEL = "Paste"
    const val CLEAR_LABEL = "Clear"
    const val BOX_LABEL = "Text message"

    /** Box contents when the screen opens: clipboard text if there is any. */
    fun initialText(clipboard: String?): String = clipboard?.trim().orEmpty()

    /**
     * Paste button. Fills an empty box with the clipboard. Text already in
     * the box is kept; the clipboard is added on a new line unless it is
     * already there.
     */
    fun paste(current: String, clipboard: String?): String {
        val clip = clipboard?.trim().orEmpty()
        if (clip.isBlank()) return current
        if (current.isBlank()) return clip
        if (current.contains(clip)) return current
        return current.trimEnd() + "\n" + clip
    }

    fun canRead(text: String): Boolean = text.isNotBlank()

    /** The text to hand off, or null when the box is empty. */
    fun handoff(text: String, id: Long): SharedText? {
        val body = text.trim()
        if (body.isBlank()) return null
        return SharedText(id = id, body = body, senderPhone = null)
    }

    /**
     * Read text: hands the text to the form that reviews it. The form runs
     * [TextMessageImport.parse] and fills empty fields only, exactly like a
     * share from the Messages app.
     */
    fun send(text: String, target: TextImportTarget, id: Long): SharedText? {
        val shared = handoff(text, id) ?: return null
        deliver(shared, target)
        return shared
    }

    /** Puts already-read text where the target form picks it up. */
    fun deliver(shared: SharedText, target: TextImportTarget) {
        when (target) {
            TextImportTarget.JOB -> TextShareInbox.offer(shared)
            TextImportTarget.CUSTOMER -> CustomerTextInbox.offer(shared)
        }
    }

    /** What the review form will read from this text. */
    fun preview(text: String): TextMessageImport.Fields =
        handoff(text, 0L)?.let { TextMessageImport.parse(it.body, it.senderPhone) }
            ?: TextMessageImport.Fields()
}
