package com.strobingn.wildlifefieldops.data.remote

import android.content.Context
import android.util.Log
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.data.auth.EncryptedSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseService @Inject constructor(
    @ApplicationContext private val context: Context
) {

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
            Log.w("SupabaseService", "Supabase not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY env vars.")
            null
        } else {
            try {
                createSupabaseClient(
                    supabaseUrl = supabaseUrl,
                    supabaseKey = supabaseKey
                ) {
                    install(Postgrest)
                    install(Auth) {
                        autoLoadFromStorage = true
                        autoSaveToStorage = true
                        alwaysAutoRefresh = true
                        sessionManager = EncryptedSessionManager(context)
                    }
                    install(Storage)
                    defaultSerializer = KotlinXSerializer(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            } catch (e: Exception) {
                Log.e("SupabaseService", "Failed to create Supabase client", e)
                null
            }
        }
    }

    val auth get() = client?.auth
    val postgrest get() = client?.postgrest
}
