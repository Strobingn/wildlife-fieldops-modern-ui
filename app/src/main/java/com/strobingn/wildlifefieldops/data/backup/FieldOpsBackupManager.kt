package com.strobingn.wildlifefieldops.data.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.data.local.AppDatabase
import com.strobingn.wildlifefieldops.util.WildlifeWhispererBrand
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

/**
 * One-tap Wildlife Whisperer field-data backup (Downloads via MediaStore) and
 * SAF restore. Pending restore is applied in [WildlifeFieldOpsApp] before Room opens.
 */
object FieldOpsBackupManager {
    const val DB_NAME = AppDatabase.NAME
    private const val TAG = "FieldOpsBackup"

    fun pendingZip(context: Context): File =
        File(context.noBackupFilesDir, FieldOpsBackupFormat.PENDING_ZIP)

    /**
     * Must run before Hilt/Room ([Application.onCreate] before `super.onCreate()`).
     */
    fun applyPendingRestore(context: Context): Boolean {
        val pending = pendingZip(context)
        if (!pending.isFile || pending.length() == 0L) return false
        val unpack = File(context.cacheDir, "fieldops_restore_unpack")
        if (unpack.exists()) unpack.deleteRecursively()
        unpack.mkdirs()
        return try {
            when (val result = FieldOpsBackupArchive.extractZip(pending, unpack)) {
                is FieldOpsBackupValidation.Invalid -> {
                    Log.e(TAG, "Pending restore rejected: ${result.reason}")
                    false
                }
                is FieldOpsBackupValidation.Ok -> {
                    FieldOpsBackupArchive.applyUnpackedBackup(
                        unpacked = unpack,
                        databaseFile = context.getDatabasePath(DB_NAME),
                        filesDir = context.filesDir
                    )
                    Log.i(
                        TAG,
                        "Restored ${WildlifeWhispererBrand.COMPANY} backup from ${result.manifest.createdAt} " +
                            "(roomVersion=${result.manifest.roomVersion.let { if (it > 0) it else "unknown" }}). " +
                            "Room will migrate to v${AppDatabase.VERSION} if needed; schema is not rewritten here."
                    )
                    true
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Pending restore failed", t)
            false
        } finally {
            pending.delete()
            unpack.deleteRecursively()
        }
    }

    fun exportToDownloads(context: Context, database: AppDatabase): String {
        checkpointWal(database)
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.isFile || dbFile.length() == 0L) {
            error("Local database is missing. Open the app once, then try again.")
        }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val displayName = "WildlifeWhisperer-FieldOps-backup-$stamp.zip"
        val tmp = File(context.cacheDir, displayName)
        try {
            val sidecars = listOf("-wal", "-shm").mapNotNull { suffix ->
                File(dbFile.path + suffix).takeIf { it.isFile }
            }
            FieldOpsBackupArchive.createZip(
                dest = tmp,
                manifest = FieldOpsBackupManifest(
                    format = FieldOpsBackupFormat.FORMAT_ID,
                    appId = FieldOpsBackupFormat.APPLICATION_ID,
                    brand = WildlifeWhispererBrand.COMPANY,
                    createdAt = Instant.now().toString(),
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    roomVersion = AppDatabase.VERSION
                ),
                dbFile = dbFile,
                extraDbSidecars = sidecars,
                photosDir = File(context.filesDir, FieldOpsBackupFormat.PHOTOS_DIR),
                datastoreDir = File(context.filesDir, FieldOpsBackupFormat.DATASTORE_DIR)
            )
            writeZipToPublicDownloads(context, tmp, displayName)
            return "Backup saved to Downloads/$displayName"
        } finally {
            tmp.delete()
        }
    }

    fun stageRestore(hostContext: Context, uri: Uri) {
        val pending = pendingZip(hostContext)
        pending.parentFile?.mkdirs()
        hostContext.contentResolver.openInputStream(uri)?.use { input ->
            pending.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected backup file")
        when (val result = FieldOpsBackupArchive.validateZip(pending)) {
            is FieldOpsBackupValidation.Ok -> Unit
            is FieldOpsBackupValidation.Invalid -> {
                pending.delete()
                error(result.reason)
            }
        }
    }

    fun restartApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        if (launch != null) {
            context.startActivity(launch)
        }
        exitProcess(0)
    }

    private fun checkpointWal(database: AppDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            cursor.moveToFirst()
        }
    }

    private fun writeZipToPublicDownloads(context: Context, zip: File, displayName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create a file in Downloads")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                zip.inputStream().use { it.copyTo(out) }
            } ?: error("Unable to write the backup to Downloads")
            val done = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }
}
