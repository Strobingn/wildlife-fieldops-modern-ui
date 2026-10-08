package com.strobingn.wildlifefieldops.data.backup

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Create / validate / extract Wildlife Whisperer field-data zip archives.
 * No Android types — unit-tested on the JVM.
 */
object FieldOpsBackupArchive {

    fun createZip(
        dest: File,
        manifest: FieldOpsBackupManifest,
        dbFile: File,
        extraDbSidecars: List<File> = emptyList(),
        photosDir: File? = null,
        datastoreDir: File? = null
    ) {
        require(dbFile.isFile && dbFile.length() > 0L) { "Database file is missing or empty" }
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zip ->
            putText(zip, FieldOpsBackupFormat.MANIFEST_NAME, FieldOpsBackupFormat.toJson(manifest))
            putFile(zip, FieldOpsBackupFormat.dbZipPath(), dbFile)
            extraDbSidecars.filter { it.isFile }.forEach { sidecar ->
                putFile(zip, "${FieldOpsBackupFormat.DB_DIR}/${sidecar.name}", sidecar)
            }
            photosDir?.takeIf { it.isDirectory }?.let { dir ->
                putDirectory(zip, FieldOpsBackupFormat.PHOTOS_DIR, dir)
            }
            datastoreDir?.takeIf { it.isDirectory }?.let { dir ->
                putDirectory(zip, FieldOpsBackupFormat.DATASTORE_DIR, dir)
            }
        }
    }

    fun validateZip(zipFile: File): FieldOpsBackupValidation {
        if (!zipFile.isFile || zipFile.length() == 0L) {
            return FieldOpsBackupValidation.Invalid("Backup file is missing or empty")
        }
        return try {
            ZipFile(zipFile).use { zip ->
                val names = mutableListOf<String>()
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    names.add(enumeration.nextElement().name)
                }
                names.forEach { name ->
                    if (!FieldOpsBackupFormat.isSafeZipPath(name)) {
                        return FieldOpsBackupValidation.Invalid("Unsafe path in backup: $name")
                    }
                }
                val manifestEntry = zip.getEntry(FieldOpsBackupFormat.MANIFEST_NAME)
                    ?: return FieldOpsBackupValidation.Invalid("Missing ${FieldOpsBackupFormat.MANIFEST_NAME}")
                val json = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val manifest = FieldOpsBackupFormat.parseManifest(json)
                    ?: return FieldOpsBackupValidation.Invalid("manifest.json is not valid JSON")
                if (manifest.format != FieldOpsBackupFormat.FORMAT_ID) {
                    return FieldOpsBackupValidation.Invalid("Unsupported backup format")
                }
                if (manifest.appId != FieldOpsBackupFormat.APPLICATION_ID) {
                    return FieldOpsBackupValidation.Invalid("Backup is for a different app")
                }
                if (manifest.roomVersion > FieldOpsBackupFormat.CURRENT_ROOM_VERSION) {
                    // Room cannot downgrade: opening this DB would crash on every launch.
                    return FieldOpsBackupValidation.Invalid(
                        "Backup was made by a newer app version (database v${manifest.roomVersion}). " +
                            "Update the app, then restore again."
                    )
                }
                val dbEntry = zip.getEntry(FieldOpsBackupFormat.dbZipPath())
                    ?: return FieldOpsBackupValidation.Invalid("Missing ${FieldOpsBackupFormat.DB_FILE}")
                if (dbEntry.size == 0L) {
                    return FieldOpsBackupValidation.Invalid("Database in backup is empty")
                }
                FieldOpsBackupValidation.Ok(manifest)
            }
        } catch (t: Throwable) {
            FieldOpsBackupValidation.Invalid(t.message ?: t.javaClass.simpleName)
        }
    }

    fun extractZip(zipFile: File, destDir: File): FieldOpsBackupValidation {
        val validated = validateZip(zipFile)
        if (validated is FieldOpsBackupValidation.Invalid) return validated
        destDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile).buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!FieldOpsBackupFormat.isSafeZipPath(name)) {
                    return FieldOpsBackupValidation.Invalid("Unsafe path in backup: $name")
                }
                val outFile = File(destDir, name)
                val canonicalDest = destDir.canonicalFile
                val canonicalOut = outFile.canonicalFile
                if (!canonicalOut.path.startsWith(canonicalDest.path + File.separator) &&
                    canonicalOut != canonicalDest
                ) {
                    return FieldOpsBackupValidation.Invalid("Zip path escaped destination")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return validated
    }

    fun applyUnpackedBackup(unpacked: File, databaseFile: File, filesDir: File) {
        val dbSrc = File(unpacked, FieldOpsBackupFormat.dbZipPath())
        require(dbSrc.isFile && dbSrc.length() > 0L) { "Unpacked backup is missing the database" }
        val userVersion = FieldOpsBackupFormat.readSqliteUserVersion(dbSrc)
        require(
            userVersion == null ||
                userVersion in FieldOpsBackupFormat.MIN_MIGRATABLE_ROOM_VERSION..FieldOpsBackupFormat.CURRENT_ROOM_VERSION
        ) {
            "Backup database version $userVersion cannot be opened by this app"
        }
        databaseFile.parentFile?.mkdirs()
        // Stage every file next to the live DB first, so a failed copy (disk full, I/O error)
        // leaves the current database untouched instead of deleted.
        // Copy the SQLite file as-is. Do not bump user_version here: a v10 (or
        // older) backup must still be v10 on disk so Room runs MIGRATION_* to v11.
        val staged = mutableListOf<Pair<File, File>>()
        try {
            val dbStage = File(databaseFile.path + ".restore-tmp")
            dbSrc.copyTo(dbStage, overwrite = true)
            staged.add(dbStage to databaseFile)
            listOf("-wal", "-shm").forEach { suffix ->
                File(unpacked, "${FieldOpsBackupFormat.DB_DIR}/${FieldOpsBackupFormat.DB_FILE}$suffix")
                    .takeIf { it.isFile }?.let { src ->
                        val stage = File(databaseFile.path + suffix + ".restore-tmp")
                        src.copyTo(stage, overwrite = true)
                        staged.add(stage to File(databaseFile.path + suffix))
                    }
            }
        } catch (t: Throwable) {
            File(databaseFile.path + ".restore-tmp").delete()
            File(databaseFile.path + "-wal.restore-tmp").delete()
            File(databaseFile.path + "-shm.restore-tmp").delete()
            throw t
        }
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            File(databaseFile.path + suffix).delete()
        }
        staged.forEach { (stage, target) ->
            if (!stage.renameTo(target)) {
                stage.copyTo(target, overwrite = true)
                stage.delete()
            }
        }

        replaceDir(File(unpacked, FieldOpsBackupFormat.PHOTOS_DIR), File(filesDir, FieldOpsBackupFormat.PHOTOS_DIR))
        replaceDir(File(unpacked, FieldOpsBackupFormat.DATASTORE_DIR), File(filesDir, FieldOpsBackupFormat.DATASTORE_DIR))
    }

    private fun replaceDir(src: File, dest: File) {
        if (dest.exists()) dest.deleteRecursively()
        if (src.isDirectory) {
            src.copyRecursively(dest, overwrite = true)
        }
    }

    private fun putText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun putFile(zip: ZipOutputStream, name: String, file: File) {
        if (FieldOpsBackupFormat.shouldExcludeRelativePath(name)) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun putDirectory(zip: ZipOutputStream, zipPrefix: String, dir: File) {
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(dir).path.replace('\\', '/')
            val name = "$zipPrefix/$relative"
            if (!FieldOpsBackupFormat.shouldExcludeRelativePath(name)) {
                putFile(zip, name, file)
            }
        }
    }
}
