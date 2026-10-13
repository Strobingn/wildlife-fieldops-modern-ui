package com.strobingn.wildlifefieldops.ai.fieldops

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
 * Day fields Sir types, US style: MM/DD/YYYY (10/13/2026, 1/5/2026 and
 * 10/13/26 also work; an old yyyy-MM-dd value still parses). Blank is a real
 * value (no date). An unparseable string is an error — callers must not
 * substitute today. Storage stays epoch millis / ISO; this is the UI edge.
 */
object FieldDate {
    const val HINT = "MM/DD/YYYY"
    const val ERROR = "Use MM/DD/YYYY"

    /** What Sir sees in a date field: MM/dd/yyyy. */
    fun formatDay(millis: Long): String =
        java.text.SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(millis))

    /** ISO yyyy-MM-dd for storage/export keys. */
    fun formatIso(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    fun parseDay(raw: String): FieldDateParse {
        val text = raw.trim()
        if (text.isEmpty()) return FieldDateParse(millis = null, error = null)
        val ymd = parts(text) ?: return FieldDateParse(millis = null, error = ERROR)
        val (y, m, d) = ymd
        if (m !in 1..12 || d !in 1..31 || y !in 1900..2200) return FieldDateParse(null, ERROR)
        val cal = Calendar.getInstance()
        cal.isLenient = false
        cal.clear()
        cal.set(y, m - 1, d, 9, 0, 0)
        val millis = runCatching { cal.timeInMillis }.getOrNull() ?: return FieldDateParse(null, ERROR)
        return FieldDateParse(millis = millis, error = null)
    }

    /** (year, month, day) or null. */
    fun parts(text: String): Triple<Int, Int, Int>? {
        US.matchEntire(text)?.let { r ->
            val (m, d, yRaw) = r.destructured
            val y = if (yRaw.length == 2) 2000 + yRaw.toInt() else yRaw.toInt()
            return Triple(y, m.toInt(), d.toInt())
        }
        ISO.matchEntire(text)?.let { r ->
            val (y, m, d) = r.destructured
            return Triple(y.toInt(), m.toInt(), d.toInt())
        }
        return null
    }

    private val US = Regex("""(\d{1,2})[/.-](\d{1,2})[/.-](\d{4}|\d{2})""")
    private val ISO = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
}
