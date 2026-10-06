package com.strobingn.wildlifefieldops.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FieldOpsBackupArchiveTest {

    @Test
    fun roundTripZipContainsDbPhotosAndDatastore() {
        val root = newTempDir()
        try {
            val db = File(root, "wildlife_fieldops.db").apply {
                writeText("SQLite format 3\u0000field-jobs")
            }
            val photos = File(root, "photos").apply { mkdirs() }
            File(photos, "job1.jpg").writeText("jpeg-bytes")
            val datastore = File(root, "datastore").apply { mkdirs() }
            File(datastore, "settings.preferences_pb").writeText("prefs")
            val zip = File(root, "backup.zip")
            FieldOpsBackupArchive.createZip(
                dest = zip,
                manifest = sampleManifest(),
                dbFile = db,
                photosDir = photos,
                datastoreDir = datastore
            )

            val unpacked = File(root, "unpacked")
            val result = FieldOpsBackupArchive.extractZip(zip, unpacked)
            assertTrue(result is FieldOpsBackupValidation.Ok)
            val ok = result as FieldOpsBackupValidation.Ok
            assertEquals(FieldOpsBackupFormat.FORMAT_ID, ok.manifest.format)
            assertEquals(FieldOpsBackupFormat.BRAND, ok.manifest.brand)
            assertTrue(File(unpacked, "db/wildlife_fieldops.db").readText().contains("field-jobs"))
            assertEquals("jpeg-bytes", File(unpacked, "photos/job1.jpg").readText())
            assertEquals("prefs", File(unpacked, "datastore/settings.preferences_pb").readText())

            val databases = File(root, "databases").apply { mkdirs() }
            val filesDir = File(root, "files").apply { mkdirs() }
            File(filesDir, "photos/old.jpg").apply { parentFile?.mkdirs(); writeText("stale") }
            FieldOpsBackupArchive.applyUnpackedBackup(
                unpacked = unpacked,
                databaseFile = File(databases, "wildlife_fieldops.db"),
                filesDir = filesDir
            )
            assertEquals("SQLite format 3\u0000field-jobs", File(databases, "wildlife_fieldops.db").readText())
            assertEquals("jpeg-bytes", File(filesDir, "photos/job1.jpg").readText())
            assertFalse(File(filesDir, "photos/old.jpg").exists())
            assertEquals("prefs", File(filesDir, "datastore/settings.preferences_pb").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun createZipSkipsGgufAndTfliteEvenInsidePhotos() {
        val root = newTempDir()
        try {
            val db = File(root, "wildlife_fieldops.db").apply { writeText("db-bytes") }
            val photos = File(root, "photos").apply { mkdirs() }
            File(photos, "keep.jpg").writeText("photo")
            File(photos, "wildlife_evidence.tflite").writeText("model")
            File(photos, "local_llm/Qwen.gguf").apply { parentFile?.mkdirs(); writeText("weights") }
            val zip = File(root, "backup.zip")
            FieldOpsBackupArchive.createZip(
                dest = zip,
                manifest = sampleManifest(),
                dbFile = db,
                photosDir = photos
            )
            val unpacked = File(root, "unpacked")
            FieldOpsBackupArchive.extractZip(zip, unpacked)
            assertTrue(File(unpacked, "photos/keep.jpg").isFile)
            assertFalse(File(unpacked, "photos/wildlife_evidence.tflite").exists())
            assertFalse(File(unpacked, "photos/local_llm/Qwen.gguf").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun validateRejectsMissingManifestAndWrongApp() {
        val root = newTempDir()
        try {
            val emptyZip = File(root, "empty.zip")
            ZipOutputStream(emptyZip.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("db/wildlife_fieldops.db"))
                zip.write("db".toByteArray())
                zip.closeEntry()
            }
            val missing = FieldOpsBackupArchive.validateZip(emptyZip)
            assertTrue(missing is FieldOpsBackupValidation.Invalid)

            val db = File(root, "wildlife_fieldops.db").apply { writeText("db-bytes") }
            val zip = File(root, "wrong-app.zip")
            FieldOpsBackupArchive.createZip(
                dest = zip,
                manifest = sampleManifest().copy(appId = "com.example.other"),
                dbFile = db
            )
            val wrong = FieldOpsBackupArchive.validateZip(zip)
            assertTrue(wrong is FieldOpsBackupValidation.Invalid)
            assertTrue((wrong as FieldOpsBackupValidation.Invalid).reason.contains("different app"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun validateRejectsZipSlipPaths() {
        val root = newTempDir()
        try {
            val zip = File(root, "slip.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("manifest.json"))
                out.write(FieldOpsBackupFormat.toJson(sampleManifest()).toByteArray())
                out.closeEntry()
                out.putNextEntry(ZipEntry("db/wildlife_fieldops.db"))
                out.write("db".toByteArray())
                out.closeEntry()
                out.putNextEntry(ZipEntry("../evil.txt"))
                out.write("nope".toByteArray())
                out.closeEntry()
            }
            val result = FieldOpsBackupArchive.validateZip(zip)
            assertTrue(result is FieldOpsBackupValidation.Invalid)
            assertTrue((result as FieldOpsBackupValidation.Invalid).reason.contains("Unsafe"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun shouldExcludeLargeModelFiles() {
        assertTrue(FieldOpsBackupFormat.shouldExcludeRelativePath("local_llm/Qwen2.5.gguf"))
        assertTrue(FieldOpsBackupFormat.shouldExcludeRelativePath("wildlife_evidence.tflite"))
        assertTrue(FieldOpsBackupFormat.shouldExcludeRelativePath("map_tiles/12/3/4.png"))
        assertFalse(FieldOpsBackupFormat.shouldExcludeRelativePath("photos/job.jpg"))
        assertFalse(FieldOpsBackupFormat.shouldExcludeRelativePath("datastore/settings.preferences_pb"))
        assertTrue(FieldOpsBackupFormat.isSafeZipPath("db/wildlife_fieldops.db"))
        assertFalse(FieldOpsBackupFormat.isSafeZipPath("../databases/x"))
    }

    @Test
    fun restorePreservesOlderSqliteUserVersionForRoomMigration() {
        val root = newTempDir()
        try {
            val db = File(root, "wildlife_fieldops.db").apply {
                writeBytes(sqliteHeaderWithUserVersion(userVersion = 10, payload = "pre-pricing-jobs"))
            }
            assertEquals(10, FieldOpsBackupFormat.readSqliteUserVersion(db))
            val zip = File(root, "v10.zip")
            FieldOpsBackupArchive.createZip(
                dest = zip,
                manifest = sampleManifest().copy(roomVersion = 10, versionName = "2.3.7-sync-backlog"),
                dbFile = db
            )
            val unpacked = File(root, "unpacked")
            val validated = FieldOpsBackupArchive.extractZip(zip, unpacked)
            assertTrue(validated is FieldOpsBackupValidation.Ok)
            assertEquals(10, (validated as FieldOpsBackupValidation.Ok).manifest.roomVersion)

            val destDb = File(root, "databases/wildlife_fieldops.db")
            FieldOpsBackupArchive.applyUnpackedBackup(
                unpacked = unpacked,
                databaseFile = destDb,
                filesDir = File(root, "files").apply { mkdirs() }
            )
            assertEquals(
                "Restored DB must stay at user_version 10 so Room runs MIGRATION_10_11",
                10,
                FieldOpsBackupFormat.readSqliteUserVersion(destDb)
            )
            assertTrue(destDb.readBytes().contentEquals(db.readBytes()))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun currentRoomVersionMatchesAppDatabaseV15() {
        assertEquals(15, FieldOpsBackupFormat.CURRENT_ROOM_VERSION)
        assertEquals(FieldOpsBackupFormat.CURRENT_ROOM_VERSION, com.strobingn.wildlifefieldops.data.local.AppDatabase.VERSION)
        val last = com.strobingn.wildlifefieldops.data.local.Migrations.ALL.last()
        assertEquals(14, last.startVersion)
        assertEquals(15, last.endVersion)
        assertEquals(12, com.strobingn.wildlifefieldops.data.local.Migrations.ALL.size)
    }

    private fun sampleManifest() = FieldOpsBackupManifest(
        format = FieldOpsBackupFormat.FORMAT_ID,
        appId = FieldOpsBackupFormat.APPLICATION_ID,
        brand = FieldOpsBackupFormat.BRAND,
        createdAt = "2026-09-30T22:00:00Z",
        versionName = "2.3.9-stable-signing",
        versionCode = 50,
        roomVersion = FieldOpsBackupFormat.CURRENT_ROOM_VERSION
    )

    private fun sqliteHeaderWithUserVersion(userVersion: Int, payload: String): ByteArray {
        val header = ByteArray(100)
        val magic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        magic.copyInto(header)
        header[60] = ((userVersion ushr 24) and 0xFF).toByte()
        header[61] = ((userVersion ushr 16) and 0xFF).toByte()
        header[62] = ((userVersion ushr 8) and 0xFF).toByte()
        header[63] = (userVersion and 0xFF).toByte()
        return header + payload.toByteArray(Charsets.UTF_8)
    }

    private fun newTempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "fieldops-backup-${System.nanoTime()}").also { it.mkdirs() }
}
