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
        databaseFile.parentFile?.mkdirs()
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            File(databaseFile.path + suffix).delete()
        }
        // Copy the SQLite file as-is. Do not bump user_version here: a v10 (or
        // older) backup must still be v10 on disk so Room runs MIGRATION_* to v11.
        dbSrc.copyTo(databaseFile, overwrite = true)
        File(unpacked, "${FieldOpsBackupFormat.DB_DIR}/${FieldOpsBackupFormat.DB_FILE}-wal")
            .takeIf { it.isFile }?.copyTo(File(databaseFile.path + "-wal"), overwrite = true)
        File(unpacked, "${FieldOpsBackupFormat.DB_DIR}/${FieldOpsBackupFormat.DB_FILE}-shm")
            .takeIf { it.isFile }?.copyTo(File(databaseFile.path + "-shm"), overwrite = true)

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
