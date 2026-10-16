package com.strobingn.wildlifefieldops.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class AppUpdateInstaller @javax.inject.Inject constructor() {
    fun canRequestInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun unknownSourcesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun install(context: Context, apk: File, packageName: String): AppUpdateInstallLaunch {
        return try {
            installWithSession(context, apk, packageName)
            AppUpdateInstallLaunch.SessionCommitted
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "PackageInstaller session failed; falling back to ACTION_VIEW", t)
            installWithView(context, apk)
            AppUpdateInstallLaunch.ExternalInstallerOpened
        }
    }

    private fun installWithSession(context: Context, apk: File, packageName: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("package", 0, apk.length()).use { dest ->
                    apk.inputStream().use { input -> input.copyTo(dest) }
                    session.fsync(dest)
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = UpdateInstallReceiver.ACTION
                    setPackage(context.packageName)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
                val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
                session.commit(pending.intentSender)
            }
        } catch (t: Throwable) {
            // A half-written session would otherwise leak (and PackageInstaller caps open sessions).
            runCatching { installer.abandonSession(sessionId) }
            throw t
        }
    }

    private fun installWithView(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0

    companion object {
        private const val TAG = "AppUpdateInstaller"
    }
}
