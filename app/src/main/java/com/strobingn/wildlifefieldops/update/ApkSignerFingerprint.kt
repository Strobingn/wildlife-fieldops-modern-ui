package com.strobingn.wildlifefieldops.update

import java.security.MessageDigest

object ApkSignerFingerprint {
    fun normalize(value: String?): String =
        value.orEmpty().trim().uppercase().replace(":", "").replace(" ", "")

    fun matches(left: String?, right: String?): Boolean {
        val a = normalize(left)
        val b = normalize(right)
        return a.isNotEmpty() && a == b
    }

    fun sha256ColonUpper(certDer: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certDer)
        return digest.joinToString(":") { byte -> "%02X".format(byte) }
    }
}
