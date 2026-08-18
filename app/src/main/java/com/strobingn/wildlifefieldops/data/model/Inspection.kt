package com.strobingn.wildlifefieldops.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class InspectionType {
    INITIAL, ROUTINE, FOLLOW_UP, EMERGENCY, PREVENTIVE, COMPLIANCE
}

enum class FindingSeverity {
    NONE, LOW, MODERATE, HIGH, CRITICAL
}

@Entity(tableName = "inspections")
data class Inspection(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val jobId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val inspectorName: String = "",
    val inspectionType: InspectionType = InspectionType.ROUTINE,
    val inspectionDate: Long = System.currentTimeMillis(),
    val findings: String = "",
    val recommendations: String = "",
    val severity: FindingSeverity = FindingSeverity.NONE,
    val speciesIdentified: String = "",
    val entryPoints: String = "",
    val damageAssessment: String = "",
    val photos: List<String> = emptyList(),
    /** Raw voice-dictated (or typed) field notes captured before AI drafts the report. */
    val voiceFieldNotes: String = "",
    /** "grok", "offline_ai", or blank if the report was written by hand. */
    val aiReportSource: String = "",
    val followUpRequired: Boolean = false,
    val followUpDate: Long? = null,
    val temperature: Float? = null,
    val weatherConditions: String = "",
    val notes: String = "",
    val humidity: Int? = null,
    val windSpeed: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
