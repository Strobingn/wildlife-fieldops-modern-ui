package com.strobingn.wildlifefieldops.ai.fieldops

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FieldDateParse(
    val millis: Long?,
    val error: String?
) {
    val ok: Boolean get() = error == null
}

/**
 * Day fields Sir types as yyyy-MM-dd. Blank is a real value (no date).
 * An unparseable string is an error — callers must not substitute today.
 */
object FieldDate {
    const val ERROR = "Use yyyy-MM-dd"

    fun formatDay(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date(millis))
    }

    fun parseDay(raw: String): FieldDateParse {
        val text = raw.trim()
        if (text.isEmpty()) return FieldDateParse(millis = null, error = null)
        if (!DAY.matches(text)) return FieldDateParse(millis = null, error = ERROR)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.isLenient = false
        val parsed = runCatching { fmt.parse(text) }.getOrNull()
            ?: return FieldDateParse(millis = null, error = ERROR)
        val cal = Calendar.getInstance()
        cal.time = parsed
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return FieldDateParse(millis = cal.timeInMillis, error = null)
    }

    private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")
}
