package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.strobingn.wildlifefieldops.ui.viewmodel.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

object BusinessProfileStore {
    private val nameKey = stringPreferencesKey(BusinessProfileResolve.KEY_NAME)
    private val addressKey = stringPreferencesKey(BusinessProfileResolve.KEY_ADDRESS)
    private val phoneKey = stringPreferencesKey(BusinessProfileResolve.KEY_PHONE)
    private val emailKey = stringPreferencesKey(BusinessProfileResolve.KEY_EMAIL)
    private val websiteKey = stringPreferencesKey(BusinessProfileResolve.KEY_WEBSITE)
    private val licenseKey = stringPreferencesKey(BusinessProfileResolve.KEY_LICENSE)
    private val logoKey = stringPreferencesKey(BusinessProfileResolve.KEY_LOGO)

    fun load(context: Context): BusinessProfile = runBlocking {
        read(context.settingsDataStore.data.first())
    }

    fun read(prefs: Preferences): BusinessProfile {
        val present = buildMap {
            fun take(key: Preferences.Key<String>) {
                if (prefs.contains(key)) put(key.name, prefs[key].orEmpty())
            }
            take(nameKey)
            take(addressKey)
            take(phoneKey)
            take(emailKey)
            take(websiteKey)
            take(licenseKey)
            take(logoKey)
        }
        return BusinessProfileResolve.fromStored(present)
    }

    fun logoFile(context: Context): File = File(context.filesDir, "business-logo")

    fun importLogo(context: Context, uri: Uri): String {
        val dest = logoFile(context)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not read the selected logo" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest.absolutePath
    }

    fun loadDocumentLogo(context: Context, profile: BusinessProfile): Bitmap? {
        if (profile.logoPath.isNotBlank()) {
            decodeFile(profile.logoPath)?.let { return it }
        }
        return WildlifeWhispererBrand.loadLogo(context)?.let { ensureArgb(it) }
    }

    private fun decodeFile(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(path)?.let { ensureArgb(it) }
    }

    private fun ensureArgb(src: Bitmap): Bitmap {
        if (src.config == Bitmap.Config.ARGB_8888) return src
        val copy = src.copy(Bitmap.Config.ARGB_8888, false) ?: return src
        if (copy !== src) src.recycle()
        return copy
    }
}
