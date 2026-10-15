package com.strobingn.wildlifefieldops.desktop

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Windows-side sync client for the same Supabase project the Android app uses.
 *
 * Read model mirrors the Android RemoteJobDto (app/.../remote/RemoteDtos.kt).
 * Writes touch ONLY the columns the Android client owns per LiveSyncPayloads
 * .JOB_WRITE_COLUMNS, and only the fields the desktop actually edits, one row
 * at a time, so a stale desktop pull can never clobber phone data.
 *
 * Trust boundary: the anon key is the same publishable key the Android app
 * ships; row-level security on Supabase is the real gate. No service-role key
 * is ever present in this client.
 */
class DesktopSyncClient(supabaseUrl: String, supabaseAnonKey: String) {

    private val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseAnonKey
    ) {
        install(Postgrest)
        install(Auth)
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    @Serializable
    data class RemoteJob(
        val id: String,
        @SerialName("customer_name") val customerName: String = "",
        val title: String = "",
        val status: String = "",
        val priority: String? = null,
        val address: String? = null,
        val town: String? = null,
        val state: String? = null,
        val species: String? = null,
        val notes: String? = null,
        val scope: String? = null,
        val estimate: Double? = null,
        @SerialName("grand_total") val grandTotal: Double? = null,
        @SerialName("scheduled_start") val scheduledStart: String? = null
    )

    suspend fun signIn(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            true
        } catch (e: Exception) {
            System.err.println("signIn failed: ${e.message}")
            false
        }
    }

    suspend fun isSignedIn(): Boolean = withContext(Dispatchers.IO) {
        runCatching { client.auth.currentSessionOrNull() != null }.getOrDefault(false)
    }

    /** Pull the live jobs table the Android app syncs to. */
    suspend fun pullJobs(): List<RemoteJob> = withContext(Dispatchers.IO) {
        client.postgrest.from("jobs").select().decodeList<RemoteJob>()
    }

    /**
     * Push a notes edit for one job. Single-column update filtered by id:
     * last-writer-wins on notes only, matching how the Android app merges.
     * Returns an error message, or null on success.
     */
    suspend fun pushJobNotes(jobId: String, notes: String): String? = withContext(Dispatchers.IO) {
        try {
            client.postgrest.from("jobs").update(
                buildJsonObject {
                    put("notes", notes)
                }
            ) {
                filter { eq("id", jobId) }
            }
            null
        } catch (e: Exception) {
            "Push failed: ${e.message}"
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { client.auth.signOut() }
    }
}
