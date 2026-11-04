package com.strobingn.wildlifefieldops.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.strobingn.wildlifefieldops.review.GoogleReviewPrefs
import com.strobingn.wildlifefieldops.review.ReviewSentLogCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleReviewSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val prefs: Flow<GoogleReviewPrefs> = context.settingsDataStore.data.map { stored ->
        GoogleReviewPrefs(
            link = stored[LINK].orEmpty(),
            template = stored[TEMPLATE].orEmpty(),
            sentAtByJob = ReviewSentLogCodec.decode(stored[SENT])
        )
    }

    suspend fun setLink(value: String) {
        context.settingsDataStore.edit { it[LINK] = value.trim() }
    }

    suspend fun setTemplate(value: String) {
        context.settingsDataStore.edit { it[TEMPLATE] = value }
    }

    suspend fun markSent(jobId: String, at: Long) {
        if (jobId.isBlank() || at <= 0L) return
        context.settingsDataStore.edit { stored ->
            val next = ReviewSentLogCodec.decode(stored[SENT]).toMutableMap()
            next[jobId] = at
            stored[SENT] = ReviewSentLogCodec.encode(next)
        }
    }

    private companion object {
        val LINK = stringPreferencesKey("google_review_link")
        val TEMPLATE = stringPreferencesKey("google_review_template")
        val SENT = stringPreferencesKey("google_review_sent_by_job")
    }
}
