package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.pricing.Money
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Serializable
data class MileageLogEntry(
    val id: String = "",
    val date: Long = 0L,
    val miles: Double = 0.0,
    val purpose: String = "",
    val jobId: String = "",
    val jobTitle: String = "",
    val rate: Double = 0.65
) {
    /** Whole cents, so a year total equals the sum of the rounded rows printed on the log. */
    val amount: Double get() = Money.times(miles, rate)
}

object MileageTaxLog {
    const val IRS_RATE_2026 = 0.70

    fun yearStart(year: Int): Long = Calendar.getInstance().apply {
        set(year, Calendar.JANUARY, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun yearEnd(year: Int): Long = yearStart(year + 1)

    fun forYear(entries: List<MileageLogEntry>, year: Int): List<MileageLogEntry> {
        val start = yearStart(year)
        val end = yearEnd(year)
        return entries.filter { it.date in start until end }.sortedBy { it.date }
    }

    fun totalMiles(entries: List<MileageLogEntry>): Double = entries.sumOf { it.miles }

    fun totalAmount(entries: List<MileageLogEntry>): Double = Money.plus(*entries.map { it.amount }.toDoubleArray())

    fun toCsv(entries: List<MileageLogEntry>): String {
        val header = "Date,Miles,Purpose,Job,Rate,Amount"
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val body = entries.joinToString("\n") { row ->
            listOf(
                fmt.format(Date(row.date)),
                "%.1f".format(Locale.US, row.miles),
                row.purpose,
                row.jobTitle,
                "%.2f".format(Locale.US, row.rate),
                "%.2f".format(Locale.US, row.amount)
            ).joinToString(",") { csvCell(it) }
        }
        return if (body.isBlank()) header else "$header\n$body"
    }

    fun upsert(entries: List<MileageLogEntry>, entry: MileageLogEntry): List<MileageLogEntry> {
        val id = entry.id.ifBlank { java.util.UUID.randomUUID().toString() }
        return (entries.filterNot { it.id == id } + entry.copy(id = id)).sortedBy { it.date }
    }

    /**
     * Move one log onto [homeJobId], dropping every other copy.
     * A ledger home stores a blank job id so the row stays "No job".
     */
    fun relocate(
        logsByJob: Map<String, List<MileageLogEntry>>,
        entry: MileageLogEntry,
        homeJobId: String
    ): Map<String, List<MileageLogEntry>> {
        val id = entry.id.ifBlank { java.util.UUID.randomUUID().toString() }
        val storedJobId = if (OpsLedger.isLedgerId(homeJobId)) "" else homeJobId
        val saved = entry.copy(id = id, jobId = storedJobId)
        val cleared = logsByJob.mapValues { (_, logs) -> logs.filterNot { it.id == id } }
        return cleared + (homeJobId to upsert(cleared[homeJobId].orEmpty(), saved))
    }

    fun suggestFromEstimate(jobId: String, jobTitle: String, miles: Double, date: Long, purpose: String = "Job travel"): MileageLogEntry =
        MileageLogEntry(
            id = "",
            date = date,
            miles = miles,
            purpose = purpose,
            jobId = jobId,
            jobTitle = jobTitle,
            rate = IRS_RATE_2026
        )

    private fun csvCell(value: String): String {
        val cleaned = value.replace("\r", " ").replace("\n", " ").trim()
        return if (cleaned.contains(',') || cleaned.contains('"')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else cleaned
    }
}
