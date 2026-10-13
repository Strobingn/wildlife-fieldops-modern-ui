package com.strobingn.wildlifefieldops.util

import android.content.Context
import com.strobingn.wildlifefieldops.ai.fieldops.TechnicianSignature

/** Settings → Your signature. Drawn once; fills the technician box on new documents. */
object TechnicianSignatureStore {
    private const val PREFS = "technician_signature"
    private const val KEY_PNG = "png"
    private const val KEY_NAME = "name"
    private const val KEY_AT = "saved_at"

    fun load(context: Context): TechnicianSignature = runCatching {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        TechnicianSignature(
            pngBase64 = p.getString(KEY_PNG, "").orEmpty(),
            name = p.getString(KEY_NAME, "").orEmpty(),
            savedAt = p.getLong(KEY_AT, 0L)
        )
    }.getOrDefault(TechnicianSignature())

    fun save(context: Context, pngBase64: String, name: String, now: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PNG, pngBase64.trim())
            .putString(KEY_NAME, name.trim())
            .putLong(KEY_AT, now)
            .apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/** Pure rule used by [DocumentContacts.fill]: Settings signature fills an empty, not-cleared technician box. */
object TechSignatureDefaults {
    fun apply(packet: StandardJobPacket, saved: TechnicianSignature): StandardJobPacket {
        if (packet.technicianSignatureCleared) {
            return packet.copy(companySignatureBase64 = "", technicianSignerName = "", technicianSignedAtMillis = null)
        }
        val png = packet.companySignatureBase64.ifBlank { saved.pngBase64 }
        val name = packet.technicianSignerName.ifBlank {
            if (packet.companySignatureBase64.isBlank()) saved.name.ifBlank { packet.technicianName } else packet.technicianName
        }
        // Ink without a typed name prints Sir's name from Settings (signature, then business owner).
        val printedName = name.ifBlank { if (png.isNotBlank()) saved.name.ifBlank { packet.profile.ownerName.trim() } else "" }
        val at = packet.technicianSignedAtMillis?.takeIf { it > 0L }
            ?: if (png.isNotBlank()) packet.nowMillis else null
        return packet.copy(companySignatureBase64 = png, technicianSignerName = printedName, technicianSignedAtMillis = at)
    }
}
