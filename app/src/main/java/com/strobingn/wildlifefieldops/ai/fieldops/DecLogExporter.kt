package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.TrapLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DecLogRow(
    val date: Long,
    val species: String,
    val location: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val disposition: String,
    val method: String,
    val catchCount: Int = 0,
    val jobTitle: String = "",
    val technician: String = "",
    val notes: String = ""
)

object DecLogExporter {

    val HEADER = "Date,Species,Location,Latitude,Longitude,Disposition,Method,Count,Job,Technician,Notes"

    val DISPOSITIONS = listOf(
        "Released on site",
        "Relocated",
        "Euthanized",
        "Found dead",
        "Escaped",
        "Empty",
        "Still set",
        "Other"
    )

    val METHODS = listOf(
        "Live cage trap",
        "One-way exclusion",
        "Body-grip",
        "Hand catch",
        "Snares",
        "Other"
    )

    fun rowsFromTraps(traps: List<TrapLog>, jobsById: Map<String, Job> = emptyMap()): List<DecLogRow> {
        return traps
            .filter { it.catchType != CatchType.NONE || it.catchCount > 0 || it.disposition.isNotBlank() }
            .sortedBy { it.checkDate }
            .map { trap ->
                val job = jobsById[trap.jobId]
                DecLogRow(
                    date = trap.checkDate,
                    species = speciesLabel(trap, job),
                    location = trap.trapLocation.ifBlank { job?.address.orEmpty() },
                    latitude = trap.latitude ?: job?.latitude,
                    longitude = trap.longitude ?: job?.longitude,
                    disposition = trap.disposition.ifBlank { inferDisposition(trap) },
                    method = trap.method.ifBlank { "Live cage trap" },
                    catchCount = trap.catchCount.coerceAtLeast(if (trap.catchType != CatchType.NONE) 1 else 0),
                    jobTitle = job?.title.orEmpty().ifBlank { job?.customerName.orEmpty() },
                    technician = trap.technicianName.ifBlank { job?.assignedTo.orEmpty() },
                    notes = trap.conditionNotes.ifBlank { trap.actionTaken }
                )
            }
    }

    fun toCsv(rows: List<DecLogRow>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val body = rows.joinToString("\n") { row ->
            listOf(
                fmt.format(Date(row.date)),
                row.species,
                row.location,
                row.latitude?.let { "%.6f".format(Locale.US, it) }.orEmpty(),
                row.longitude?.let { "%.6f".format(Locale.US, it) }.orEmpty(),
                row.disposition,
                row.method,
                row.catchCount.toString(),
                row.jobTitle,
                row.technician,
                row.notes
            ).joinToString(",") { csvCell(it) }
        }
        return if (body.isBlank()) HEADER else "$HEADER\n$body"
    }

    private fun speciesLabel(trap: TrapLog, job: Job?): String {
        if (trap.catchType != CatchType.NONE) {
            return trap.catchType.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }
        }
        return job?.confirmedSpecies.orEmpty().ifBlank { job?.type.orEmpty() }.ifBlank { "Unknown" }
    }

    private fun inferDisposition(trap: TrapLog): String = when {
        trap.catchType == CatchType.NONE && trap.catchCount == 0 -> "Empty"
        trap.actionTaken.contains("releas", ignoreCase = true) -> "Released on site"
        trap.actionTaken.contains("relocat", ignoreCase = true) -> "Relocated"
        trap.actionTaken.contains("euth", ignoreCase = true) -> "Euthanized"
        else -> trap.actionTaken.ifBlank { "Other" }
    }

    private fun csvCell(value: String): String {
        val cleaned = value.replace("\r", " ").replace("\n", " ").trim()
        return if (cleaned.contains(',') || cleaned.contains('"')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }
}
