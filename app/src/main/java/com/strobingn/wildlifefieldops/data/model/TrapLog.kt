package com.strobingn.wildlifefieldops.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TrapStatus {
    SET, TRIGGERED, EMPTY, DISABLED, REMOVED, NEEDS_BAIT
}

enum class CatchType {
    NONE, RACCOON, SQUIRREL, SKUNK, OPOSSUM, BAT, BIRD, SNAKE, RODENT, FOX,
    COYOTE, GROUNDHOG, CHIPMUNK, BEAVER, OTHER
}

@Entity(tableName = "trap_logs")
data class TrapLog(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val jobId: String = "",
    val trapId: String = "",
    val trapLocation: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val technicianName: String = "",
    val checkDate: Long = System.currentTimeMillis(),
    val status: TrapStatus = TrapStatus.SET,
    val catchType: CatchType = CatchType.NONE,
    val catchCount: Int = 0,
    val baitType: String = "",
    val baitCondition: String = "",
    val conditionNotes: String = "",
    val actionTaken: String = "",
    val photoPath: String = "",
    val nextCheckDate: Long? = null,
    val weatherConditions: String = "",
    val temperature: Float? = null,
    /** NY DEC disposition (released / relocated / euthanized / empty / …). */
    val disposition: String = "",
    /** Take method (live cage, one-way, hand catch, …). */
    val method: String = "",
    /** Hours between checks for this trap. Null uses the Settings default (24 hours). */
    val checkIntervalHours: Int? = null,
    /** 1-sigma meters of the accuracy-weighted GPS average. Null when coordinates were typed or tapped. */
    val locationAccuracyM: Double? = null,
    /** Accepted fixes (accuracy ≤ 15 m) that went into [locationAccuracyM]. */
    val locationSampleCount: Int? = null,
    /** Wall-clock millis when Precise GPS saved this spot. */
    val locationCapturedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
