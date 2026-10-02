package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.tax.NyCountyTaxRates
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Official NYS DEC Nuisance Wildlife Control Log columns (form revised 8/2024,
 * https://dec.ny.gov/sites/default/files/2024-08/nuisancewildlifecontrollog.pdf).
 *
 * Header: 1 Name (Last, First, MI) · 2 NWCO license # · 3 DEC region · 4 County of residence.
 * Rows: 5 complainant · 6 date(s) performed · 7 nuisance species · 8 complaint type
 * · 9 abatement method · 10 area of complaint · 11 number of traps · 12 species and
 * number taken · 13 disposition (E / R + county / T + rehab license).
 *
 * Codes still match the instruction sheet on that PDF.
 */
@Serializable
data class NwcoOperatorProfile(
    val lastName: String = "",
    val firstName: String = "",
    val middleInitial: String = "",
    val address: String = "",
    val phone: String = "",
    val licenseNumber: String = "",
    val decRegion: String = "",
    val countyOfResidence: String = ""
) {
    fun displayName(): String = listOf(lastName, firstName, middleInitial)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .ifBlank { listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ") }
}

@Serializable
data class NwcoLogRecord(
    val id: String = UUID.randomUUID().toString(),
    val sourceKey: String = "",
    val jobId: String = "",
    val trapId: String = "",
    val workDate: Long = System.currentTimeMillis(),
    val complainant: String = "",
    val datesPerformed: String = "",
    val species: String = "",
    val complaintType: String = "",
    val abatementMethod: String = "",
    val areaOfComplaint: String = "",
    val trapsSet: String = "",
    val speciesAndNumberTaken: String = "",
    val disposition: String = "",
    val county: String = "",
    val town: String = "",
    val locked: Set<String> = emptySet(),
    val deleted: Boolean = false,
    val manual: Boolean = false
)

data class NwcoAutoInput(
    val jobs: List<Job>,
    val traps: List<TrapLog>,
    val inspections: List<Inspection> = emptyList(),
    val photos: List<Photo> = emptyList()
)

object DecNwcoLog {

    val COMPLAINT_TYPES = listOf(
        "A — Animal sick or injured",
        "B — Animal doing damage to property",
        "C — Animal menacing pets or animals",
        "D — Other"
    )

    val METHODS = listOf(
        "A — Box trap",
        "B — Foot hold trap",
        "C — Body gripping trap",
        "D — Hand or catchpole",
        "E — Shooting",
        "F — Exclusion",
        "G — Habitat modification",
        "H — Other"
    )

    val AREAS = listOf(
        "A — Urban",
        "B — Suburban",
        "C — Rural"
    )

    val DISPOSITIONS = listOf(
        "E — Euthanized",
        "R — Released",
        "T — Transferred to licensed rehabilitator"
    )

    val CSV_HEADER = listOf(
        "Licensee name",
        "NWCO license number",
        "DEC region",
        "County of residence",
        "5 Name and address of complainant",
        "6 Date(s) performed",
        "7 Nuisance species",
        "8 Complaint type",
        "9 Abatement method",
        "10 Area of complaint",
        "11 Number of traps",
        "12 Species and number taken",
        "13 Disposition of animal",
        "County",
        "Town",
        "Job id"
    ).joinToString(",")

    val CELL_KEYS = listOf(
        "complainant",
        "datesPerformed",
        "species",
        "complaintType",
        "abatementMethod",
        "areaOfComplaint",
        "trapsSet",
        "speciesAndNumberTaken",
        "disposition"
    )

    fun licenseYearBounds(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val startYear = if (month >= Calendar.OCTOBER) year else year - 1
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, startYear)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.YEAR, 1)
        }.timeInMillis
        return start to end
    }

    fun autoFill(input: NwcoAutoInput): List<NwcoLogRecord> {
        val trapsByJob = input.traps.groupBy { it.jobId }
        val inspectionsByJob = input.inspections.groupBy { it.jobId }
        val photosByJob = input.photos.groupBy { it.jobId.orEmpty() }
        return input.jobs
            .filterNot { OpsLedger.isLedger(it) }
            .flatMap { job ->
                val jobTraps = trapsByJob[job.id].orEmpty()
                val catchTraps = jobTraps.filter { it.catchType != CatchType.NONE || it.catchCount > 0 }
                if (catchTraps.isEmpty()) {
                    listOf(rowFromJob(job, jobTraps, inspectionsByJob[job.id].orEmpty(), photosByJob[job.id].orEmpty()))
                } else {
                    catchTraps
                        .groupBy { speciesFromTrap(it, job) }
                        .map { (_, group) ->
                            rowFromJob(
                                job = job,
                                traps = group,
                                inspections = inspectionsByJob[job.id].orEmpty(),
                                photos = photosByJob[job.id].orEmpty(),
                                trap = group.maxByOrNull { it.checkDate },
                                takenOverride = group.sumOf { it.catchCount.coerceAtLeast(1) }
                            )
                        }
                }
            }
            .sortedBy { it.workDate }
    }

    /**
     * Saved rows win on locked cells and on [NwcoLogRecord.deleted]. Auto-fill
     * never overwrites a typed cell. Manual extras (no matching auto source) stay.
     */
    fun merge(autoRows: List<NwcoLogRecord>, saved: List<NwcoLogRecord>): List<NwcoLogRecord> {
        val savedByKey = saved.associateBy { it.sourceKey.ifBlank { it.id } }
        val mergedAuto = autoRows.mapNotNull { auto ->
            val prior = savedByKey[auto.sourceKey]
            if (prior?.deleted == true) return@mapNotNull null
            if (prior == null) auto else overlay(auto, prior)
        }
        val autoKeys = autoRows.map { it.sourceKey }.toSet()
        val extras = saved.filter { rec ->
            !rec.deleted && (rec.manual || rec.sourceKey !in autoKeys)
        }
        return (mergedAuto + extras).sortedBy { it.workDate }
    }

    fun overlay(auto: NwcoLogRecord, saved: NwcoLogRecord): NwcoLogRecord {
        val locked = saved.locked
        return auto.copy(
            id = saved.id.ifBlank { auto.id },
            locked = locked,
            deleted = saved.deleted,
            manual = saved.manual,
            complainant = if ("complainant" in locked) saved.complainant else auto.complainant,
            datesPerformed = if ("datesPerformed" in locked) saved.datesPerformed else auto.datesPerformed,
            workDate = if ("datesPerformed" in locked) saved.workDate else auto.workDate,
            species = if ("species" in locked) saved.species else auto.species,
            complaintType = if ("complaintType" in locked) saved.complaintType else auto.complaintType,
            abatementMethod = if ("abatementMethod" in locked) saved.abatementMethod else auto.abatementMethod,
            areaOfComplaint = if ("areaOfComplaint" in locked) saved.areaOfComplaint else auto.areaOfComplaint,
            trapsSet = if ("trapsSet" in locked) saved.trapsSet else auto.trapsSet,
            speciesAndNumberTaken = if ("speciesAndNumberTaken" in locked) saved.speciesAndNumberTaken else auto.speciesAndNumberTaken,
            disposition = if ("disposition" in locked) saved.disposition else auto.disposition,
            county = if ("county" in locked) saved.county else auto.county,
            town = if ("town" in locked) saved.town else auto.town
        )
    }

    fun withTyped(record: NwcoLogRecord, key: String, value: String): NwcoLogRecord {
        val next = when (key) {
            "complainant" -> record.copy(complainant = value)
            "datesPerformed" -> record.copy(datesPerformed = value)
            "species" -> record.copy(species = value)
            "complaintType" -> record.copy(complaintType = value)
            "abatementMethod" -> record.copy(abatementMethod = value)
            "areaOfComplaint" -> record.copy(areaOfComplaint = value)
            "trapsSet" -> record.copy(trapsSet = value)
            "speciesAndNumberTaken" -> record.copy(speciesAndNumberTaken = value)
            "disposition" -> record.copy(disposition = value)
            "county" -> record.copy(county = value)
            "town" -> record.copy(town = value)
            else -> record
        }
        return next.copy(locked = record.locked + key)
    }

    fun blankManual(now: Long = System.currentTimeMillis()): NwcoLogRecord {
        val id = UUID.randomUUID().toString()
        return NwcoLogRecord(
            id = id,
            sourceKey = "manual:$id",
            workDate = now,
            datesPerformed = dateLabel(now),
            manual = true,
            locked = CELL_KEYS.toSet()
        )
    }

    fun toCsv(operator: NwcoOperatorProfile, rows: List<NwcoLogRecord>): String {
        val name = operator.displayName()
        val body = rows.filterNot { it.deleted }.joinToString("\n") { row ->
            listOf(
                name,
                operator.licenseNumber,
                operator.decRegion,
                operator.countyOfResidence,
                row.complainant,
                row.datesPerformed.ifBlank { dateLabel(row.workDate) },
                row.species,
                row.complaintType,
                row.abatementMethod,
                row.areaOfComplaint,
                row.trapsSet,
                row.speciesAndNumberTaken,
                row.disposition,
                row.county,
                row.town,
                row.jobId
            ).joinToString(",") { csvCell(it) }
        }
        return if (body.isBlank()) CSV_HEADER else "$CSV_HEADER\n$body"
    }

    fun filterYear(rows: List<NwcoLogRecord>, now: Long = System.currentTimeMillis()): List<NwcoLogRecord> {
        val (start, end) = licenseYearBounds(now)
        return rows.filter { it.workDate in start until end }
    }

    fun filterCalendarYear(rows: List<NwcoLogRecord>, year: Int): List<NwcoLogRecord> {
        val start = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year + 1, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return rows.filter { it.workDate in start until end }
    }

    private fun rowFromJob(
        job: Job,
        traps: List<TrapLog>,
        inspections: List<Inspection>,
        photos: List<Photo>,
        trap: TrapLog? = null,
        takenOverride: Int? = null
    ): NwcoLogRecord {
        val species = speciesFromTrap(trap, job)
            .ifBlank { speciesFromTags(job, inspections, photos) }
            .ifBlank { "Unknown" }
        val taken = takenOverride ?: traps.filter { it.catchCount > 0 || it.catchType != CatchType.NONE }
            .sumOf { it.catchCount.coerceAtLeast(1) }
        val trapDate = trap?.checkDate?.takeIf { it > 0L }
        val date = trapDate ?: job.completedDate ?: job.scheduledDate ?: job.createdAt
        val county = job.county.orEmpty().ifBlank { countyFromAddress(job.address) }
        val town = townFromAddress(job.address)
        val source = if (trap != null) "job:${job.id}:trap:${trap.id}:$species" else "job:${job.id}"
        return NwcoLogRecord(
            sourceKey = source,
            jobId = job.id,
            trapId = trap?.id.orEmpty(),
            workDate = date,
            complainant = complainant(job),
            datesPerformed = dateLabel(date),
            species = specificSpecies(species),
            complaintType = inferComplaint(job, inspections, photos),
            abatementMethod = inferMethod(trap, traps, job),
            areaOfComplaint = inferArea(job),
            trapsSet = traps.size.takeIf { it > 0 }?.toString().orEmpty(),
            speciesAndNumberTaken = if (taken > 0) "$species $taken" else "",
            disposition = inferDisposition(trap, traps),
            county = if (county.isBlank()) "" else NyCountyTaxRates.displayName(county),
            town = town
        )
    }

    private fun complainant(job: Job): String =
        listOf(job.customerName, job.address).filter { it.isNotBlank() }.joinToString("\n")

    private fun speciesFromTrap(trap: TrapLog?, job: Job): String {
        if (trap != null && trap.catchType != CatchType.NONE) {
            return specificSpecies(trap.catchType.name.lowercase().replace('_', ' '))
        }
        return job.confirmedSpecies.ifBlank { job.type }
    }

    private fun speciesFromTags(job: Job, inspections: List<Inspection>, photos: List<Photo>): String {
        val fromPricing = job.pricing.photoAutoTags.map { it.species }.firstOrNull { it.isNotBlank() }
        val fromInspection = inspections.map { it.speciesIdentified }.firstOrNull { it.isNotBlank() }
        val fromPhoto = photos.map { it.description }.firstOrNull { it.isNotBlank() }
        return fromPricing.orEmpty().ifBlank { fromInspection.orEmpty() }.ifBlank { fromPhoto.orEmpty() }
    }

    private fun specificSpecies(raw: String): String {
        val t = raw.trim()
        if (t.isBlank()) return ""
        return t.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }
    }

    private fun inferComplaint(job: Job, inspections: List<Inspection>, photos: List<Photo>): String {
        val blob = listOf(
            job.description,
            job.notes,
            job.type,
            job.legalNotes,
            inspections.joinToString(" ") { it.findings + " " + it.recommendations },
            photos.joinToString(" ") { it.description },
            job.pricing.photoAutoTags.joinToString(" ") { it.asDescription() }
        ).joinToString(" ").lowercase(Locale.US)
        return when {
            blob.contains("sick") || blob.contains("injur") || blob.contains("rabid") -> COMPLAINT_TYPES[0]
            blob.contains("pet") || blob.contains("dog") || blob.contains("cat") || blob.contains("menac") ->
                COMPLAINT_TYPES[2]
            blob.contains("damage") || blob.contains("chew") || blob.contains("attic") ||
                blob.contains("exclusion") || blob.contains("droppings") || blob.contains("entry") ->
                COMPLAINT_TYPES[1]
            else -> COMPLAINT_TYPES[1]
        }
    }

    private fun inferMethod(trap: TrapLog?, traps: List<TrapLog>, job: Job): String {
        val method = trap?.method?.ifBlank { null } ?: traps.firstOrNull { it.method.isNotBlank() }?.method.orEmpty()
        val trapBlob = method.lowercase(Locale.US)
        val jobBlob = (job.type + " " + job.notes + " " + job.description).lowercase(Locale.US)
        fun match(blob: String): String? = when {
            blob.contains("body") || blob.contains("conibear") -> METHODS[2]
            blob.contains("foot") || blob.contains("leghold") -> METHODS[1]
            blob.contains("shoot") -> METHODS[4]
            blob.contains("hand") || blob.contains("catchpole") || blob.contains("catch pole") -> METHODS[3]
            blob.contains("habitat") || blob.contains("trim") -> METHODS[6]
            blob.contains("cage") || blob.contains("box") || blob.contains("live") -> METHODS[0]
            blob.contains("exclusion") || blob.contains("one-way") || blob.contains("seal") || blob.contains("exclu") -> METHODS[5]
            else -> null
        }
        return match(trapBlob) ?: match(jobBlob) ?: if (traps.isNotEmpty()) METHODS[0] else METHODS[7]
    }

    private fun inferArea(job: Job): String {
        val blob = (job.address + " " + job.notes + " " + job.description).lowercase(Locale.US)
        return when {
            blob.contains("rural") || blob.contains("farm") || blob.contains("forest") -> AREAS[2]
            blob.contains("urban") || blob.contains("city") || blob.contains("apartment") -> AREAS[0]
            else -> AREAS[1]
        }
    }

    private fun inferDisposition(trap: TrapLog?, traps: List<TrapLog>): String {
        val text = listOfNotNull(trap?.disposition, trap?.actionTaken)
            .plus(traps.map { it.disposition })
            .plus(traps.map { it.actionTaken })
            .joinToString(" ")
            .lowercase(Locale.US)
        return when {
            text.contains("euth") || text.contains("lethal") -> DISPOSITIONS[0]
            text.contains("rehab") || text.contains("transfer") || text.contains("rehabilit") -> DISPOSITIONS[2]
            text.contains("relocat") -> "R — Released"
            text.contains("releas") -> DISPOSITIONS[1]
            trap != null && trap.catchCount > 0 -> DISPOSITIONS[1]
            else -> ""
        }
    }

    private fun countyFromAddress(address: String): String {
        val parts = address.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return parts.firstOrNull { it.contains("county", ignoreCase = true) }.orEmpty()
    }

    private fun townFromAddress(address: String): String {
        val parts = address.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return parts.getOrNull(1).orEmpty()
    }

    private fun dateLabel(ms: Long): String =
        SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(ms))

    private fun csvCell(value: String): String {
        val cleaned = value.replace("\r", " ").replace("\n", " ").trim()
        return if (cleaned.contains(',') || cleaned.contains('"')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else cleaned
    }
}
