package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.TrapLog

/**
 * Turns a caught-animal trap check into a new DEC log entry, only when Sir taps for it.
 * Only empty cells of the new entry are filled. Nothing here runs on its own.
 */
object DecCatchPrefill {

    fun prefillEmpty(target: NwcoLogRecord, source: NwcoLogRecord): NwcoLogRecord {
        fun pick(current: String, suggested: String) = if (current.isBlank()) suggested else current
        return target.copy(
            jobId = pick(target.jobId, source.jobId),
            trapId = pick(target.trapId, source.trapId),
            complainant = pick(target.complainant, source.complainant),
            datesPerformed = pick(target.datesPerformed, source.datesPerformed),
            species = pick(target.species, source.species),
            complaintType = pick(target.complaintType, source.complaintType),
            abatementMethod = pick(target.abatementMethod, source.abatementMethod),
            areaOfComplaint = pick(target.areaOfComplaint, source.areaOfComplaint),
            trapsSet = pick(target.trapsSet, source.trapsSet),
            speciesAndNumberTaken = pick(target.speciesAndNumberTaken, source.speciesAndNumberTaken),
            disposition = pick(target.disposition, source.disposition),
            county = pick(target.county, source.county),
            town = pick(target.town, source.town)
        )
    }

    /** What the DEC compiler would write for this catch, used only as a prefill source. */
    fun sourceRow(job: Job?, trap: TrapLog): NwcoLogRecord {
        val owner = job ?: Job(id = trap.jobId, title = "")
        val rows = DecNwcoLog.autoFill(NwcoAutoInput(jobs = listOf(owner), traps = listOf(trap)))
        return rows.firstOrNull { it.trapId == trap.id } ?: rows.firstOrNull() ?: NwcoLogRecord(trapId = trap.id)
    }

    fun newEntry(job: Job?, trap: TrapLog, now: Long = System.currentTimeMillis()): NwcoLogRecord {
        val date = trap.checkDate.takeIf { it > 0L } ?: now
        return prefillEmpty(DecNwcoLog.blankManual(date), sourceRow(job, trap))
    }
}
