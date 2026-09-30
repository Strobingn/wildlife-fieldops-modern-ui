package com.strobingn.wildlifefieldops.data.backup

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * On-device Wildlife Whisperer field-data backup format (zip + JSON manifest).
 * Keep this JVM-safe so unit tests can validate archives without Android.
 */
object FieldOpsBackupFormat {
    const val FORMAT_ID = "wildlife-fieldops-backup-v1"
    const val APPLICATION_ID = "com.strobingn.wildlifefieldops"
    const val BRAND = "Wildlife Whisperer LLC"
    const val MANIFEST_NAME = "manifest.json"
    const val DB_DIR = "db"
    const val DB_FILE = "wildlife_fieldops.db"
    const val PHOTOS_DIR = "photos"
    const val DATASTORE_DIR = "datastore"
    const val PENDING_ZIP = "pending_restore.zip"

    private val gson = Gson()

    fun dbZipPath(): String = "$DB_DIR/$DB_FILE"

    fun parseManifest(json: String): FieldOpsBackupManifest? {
        return try {
            gson.fromJson(json, FieldOpsBackupManifest::class.java)
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    fun toJson(manifest: FieldOpsBackupManifest): String = gson.toJson(manifest)

    fun shouldExcludeRelativePath(relativePath: String): Boolean {
        val lower = relativePath.replace('\\', '/').lowercase()
        if (lower.endsWith(".gguf") || lower.endsWith(".tflite") || lower.endsWith(".task")) return true
        val segments = lower.split('/')
        if (segments.any { it == "local_llm" || it == "map_tiles" }) return true
        if (segments.lastOrNull() == "wildlife_evidence.tflite") return true
        return false
    }

    fun isSafeZipPath(name: String): Boolean {
        if (name.isBlank()) return false
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.startsWith("../") || normalized.contains("/../")) return false
        if (normalized == ".." || normalized.endsWith("/..")) return false
        return true
    }
}

data class FieldOpsBackupManifest(
    val format: String = FieldOpsBackupFormat.FORMAT_ID,
    val appId: String = FieldOpsBackupFormat.APPLICATION_ID,
    val brand: String = FieldOpsBackupFormat.BRAND,
    val createdAt: String = "",
    val versionName: String = "",
    val versionCode: Int = 0
)

sealed class FieldOpsBackupValidation {
    data class Ok(val manifest: FieldOpsBackupManifest) : FieldOpsBackupValidation()
    data class Invalid(val reason: String) : FieldOpsBackupValidation()
}
