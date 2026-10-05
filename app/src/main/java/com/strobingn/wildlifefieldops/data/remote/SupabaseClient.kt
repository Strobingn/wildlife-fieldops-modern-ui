package com.strobingn.wildlifefieldops.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import com.strobingn.wildlifefieldops.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseService @Inject constructor() {

    // Keys from BuildConfig (GitHub Actions secrets / local env at build time)
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY

    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() &&
            !supabaseUrl.contains("your-project") &&
            supabaseKey.isNotBlank() &&
            supabaseKey != "your-anon-key" &&
            !supabaseKey.contains("your_supabase", ignoreCase = true)

    val client: SupabaseClient? by lazy {
        if (!isConfigured) {
            android.util.Log.w("SupabaseService", "Supabase not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY env vars.")
            null
        } else {
            try {
                createSupabaseClient(
                    supabaseUrl = supabaseUrl,
                    supabaseKey = supabaseKey
                ) {
                    install(Postgrest)
                    install(Auth) {
                        // Auth configuration
                    }
                    install(Storage) {
                        // Storage configuration
                    }
                    defaultSerializer = KotlinXSerializer(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseService", "Failed to create Supabase client", e)
                null
            }
        }
    }

    val auth get() = client?.auth
    val postgrest get() = client?.postgrest

    /** Anon or publishable key already shipped for PostgREST. Not an LLM secret. */
    val anonOrPublishableKey: String get() = supabaseKey

    fun edgeFunctionUrl(functionName: String): String =
        supabaseUrl.trimEnd('/') + "/functions/v1/" + functionName

    /**
     * User access token when a session exists; otherwise the anon/publishable key.
     * Edge Functions with verify_jwt accept the legacy anon JWT and user sessions.
     */
    fun edgeAuthorizationBearer(): String {
        val sessionToken = runCatching {
            val status = client?.auth?.sessionStatus?.value
            if (status is SessionStatus.Authenticated) status.session.accessToken else null
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        return sessionToken ?: supabaseKey
    }

    suspend fun signIn(email: String, password: String): Boolean {
        return try {
            auth?.signInWith(Email) {
                this.email = email
                this.password = password
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun signUp(email: String, password: String): Boolean {
        return try {
            auth?.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isAuthenticated(): Boolean {
        return auth?.currentUserOrNull() != null
    }

    suspend fun signInAnonymous(): Boolean {
        return try {
            val uuid = java.util.UUID.randomUUID().toString()
            auth?.signUpWith(Email) {
                this.email = "anon_${uuid}@wildlifefieldops.local"
                this.password = uuid
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}