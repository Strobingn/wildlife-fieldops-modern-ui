package com.strobingn.wildlifefieldops.data.backup

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File

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
    /** Matches [com.strobingn.wildlifefieldops.data.local.AppDatabase.VERSION]. */
    const val CURRENT_ROOM_VERSION = 12
    private const val SQLITE_USER_VERSION_OFFSET = 60

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

    /**
     * SQLite user_version at header offset 60 (big-endian). Restore must leave this
     * unchanged so Room can run MIGRATION_* up to [CURRENT_ROOM_VERSION].
     */
    fun readSqliteUserVersion(dbFile: File): Int? {
        if (!dbFile.isFile || dbFile.length() < SQLITE_USER_VERSION_OFFSET + 4L) return null
        val header = ByteArray(SQLITE_USER_VERSION_OFFSET + 4)
        dbFile.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read <= 0) return null
                offset += read
            }
        }
        val magic = header.copyOfRange(0, 16).toString(Charsets.US_ASCII)
        if (!magic.startsWith("SQLite format 3")) return null
        return ((header[60].toInt() and 0xFF) shl 24) or
            ((header[61].toInt() and 0xFF) shl 16) or
            ((header[62].toInt() and 0xFF) shl 8) or
            (header[63].toInt() and 0xFF)
    }
}

data class FieldOpsBackupManifest(
    val format: String = FieldOpsBackupFormat.FORMAT_ID,
    val appId: String = FieldOpsBackupFormat.APPLICATION_ID,
    val brand: String = FieldOpsBackupFormat.BRAND,
    val createdAt: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val roomVersion: Int = 0
)

sealed class FieldOpsBackupValidation {
    data class Ok(val manifest: FieldOpsBackupManifest) : FieldOpsBackupValidation()
    data class Invalid(val reason: String) : FieldOpsBackupValidation()
}
