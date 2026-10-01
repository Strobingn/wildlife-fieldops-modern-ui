package com.strobingn.wildlifefieldops.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.jar.JarFile

class ApkPackageInspector @javax.inject.Inject constructor() {
    fun inspectInstalled(context: Context, packageName: String = context.packageName): ApkIdentity? {
        val info = installedInfo(context.packageManager, packageName) ?: return null
        return identityFrom(info, archive = null)
    }

    fun inspectArchive(context: Context, apk: File): ApkIdentity? {
        if (!apk.isFile) return null
        val header = apk.inputStream().use { stream ->
            val bytes = ByteArray(4)
            val n = stream.read(bytes)
            if (n < 4) ByteArray(0) else bytes
        }
        if (!ApkInstallVerification.looksLikeApk(header)) return null
        val info = archiveInfo(context.packageManager, apk)
        info?.applicationInfo?.apply {
            sourceDir = apk.absolutePath
            publicSourceDir = apk.absolutePath
        }
        return identityFrom(info, archive = apk)
    }

    private fun identityFrom(info: PackageInfo?, archive: File?): ApkIdentity? {
        if (info == null) return null
        val packageName = info.packageName ?: return null
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
        val signer = signerFromPackage(info) ?: archive?.let { signerFromJar(it) } ?: return null
        return ApkIdentity(packageName = packageName, versionCode = versionCode, signerSha256 = signer)
    }

    private fun signerFromPackage(info: PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
                ?: info.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val first = signatures?.firstOrNull()?.toByteArray() ?: return null
        return ApkSignerFingerprint.sha256ColonUpper(first)
    }

    private fun signerFromJar(apk: File): String? {
        return runCatching {
            JarFile(apk, true).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    jar.getInputStream(entry).use { input ->
                        val buffer = ByteArray(8 * 1024)
                        while (input.read(buffer) != -1) {
                            // Must drain the entry before certificates are available.
                        }
                    }
                    val cert = entry.certificates?.firstOrNull() ?: continue
                    return@use ApkSignerFingerprint.sha256ColonUpper(cert.encoded)
                }
                null
            }
        }.getOrNull()
    }

    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo? {
        val flags = signingFlags()
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        }.getOrNull()
    }

    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? {
        val flags = signingFlags()
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.absolutePath, flags)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int {
        var flags = PackageManager.GET_SIGNING_CERTIFICATES
        flags = flags or PackageManager.GET_SIGNATURES
        return flags
    }
}
