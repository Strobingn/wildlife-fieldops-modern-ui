package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.DistanceService
import com.strobingn.wildlifefieldops.data.remote.EstimateDraft
import com.strobingn.wildlifefieldops.data.remote.GeocodingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

@HiltViewModel
class JobAiViewModel @Inject constructor(
    private val aiService: AiService,
    private val jobDao: JobDao,
    private val distanceService: DistanceService,
    private val geocodingService: GeocodingService,
    private val shopSettings: ShopSettings
) : ViewModel() {

    val isConfigured: Boolean get() = aiService.isConfigured
    val providerLabel: String get() = aiService.providerLabel

    private val _summary = MutableStateFlow<String?>(null)
    val summary: StateFlow<String?> = _summary.asStateFlow()

    private val _summaryLoading = MutableStateFlow(false)
    val summaryLoading: StateFlow<Boolean> = _summaryLoading.asStateFlow()

    private val _estimateDraft = MutableStateFlow<EstimateDraft?>(null)
    val estimateDraft: StateFlow<EstimateDraft?> = _estimateDraft.asStateFlow()

    private val _estimateLoading = MutableStateFlow(false)
    val estimateLoading: StateFlow<Boolean> = _estimateLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun generateSummary(job: Job) {
        if (_summaryLoading.value) return
        _summaryLoading.value = true
        _message.value = null
        viewModelScope.launch {
            _summary.value = aiService.summarizeJob(job)
            _summaryLoading.value = false
        }
    }

    fun draftEstimate(job: Job) {
        if (_estimateLoading.value) return
        _estimateLoading.value = true
        _message.value = null
        viewModelScope.launch {
            val shop = shopSettings.address()
            val tax = shopSettings.taxPercent()
            val dest = job.address.trim()
            var miles: Double? = null
            var noteForAi = ""
            var uiMessage = ""
            when {
                shop.isBlank() -> {
                    noteForAi = "Shop address missing — set mileage 0."
                    uiMessage = "Add your shop address in Settings → Mileage / shop base."
                }
                dest.isBlank() -> {
                    noteForAi = "Job has no address — set mileage 0."
                    uiMessage = "Job has no address — mileage left at 0."
                }
                else -> {
                    val looked = distanceService.drivingMiles(shop, dest)
                    looked.fold(
                        onSuccess = { d ->
                            miles = d.miles
                            noteForAi =
                                "MEASURED driving miles shop→job: ${d.miles} (Google Distance Matrix, ${d.durationText.ifBlank { "time n/a" }})."
                            uiMessage =
                                "AI draft ready — mileage ${d.miles} mi driving · tax $tax%. Review before quoting."
                        },
                        onFailure = { err ->
                            val approx = approximateRoadMiles(shop, dest, job.latitude, job.longitude)
                            if (approx != null) {
                                miles = approx
                                noteForAi =
                                    "APPROX road miles shop→job: $approx (geocode straight-line × 1.25; Distance Matrix unavailable)."
                                uiMessage =
                                    "AI draft ready — ~$approx mi estimated (Maps Distance Matrix blocked on this key). Tax $tax%."
                            } else {
                                noteForAi = "Mileage unavailable — set mileage 0. Do not invent miles."
                                uiMessage = friendlyDistanceError(err.message)
                            }
                        }
                    )
                }
            }
            val draft = aiService.draftEstimateFromJob(
                job = job,
                drivingMiles = miles,
                taxPercent = tax,
                distanceNote = noteForAi
            )
            _estimateDraft.value = draft
            _estimateLoading.value = false
            _message.value = uiMessage.ifBlank {
                if (miles != null) "AI draft ready — review mileage and tax."
                else "AI draft ready — review mileage and tax before quoting."
            }
        }
    }

    fun appendSummaryToNotes(job: Job) {
        val text = _summary.value?.trim().orEmpty()
        if (text.isBlank()) return
        viewModelScope.launch {
            val existing = jobDao.getById(job.id) ?: job
            val stamp = "\n\n--- AI summary ---\n$text"
            val notes = if (existing.notes.contains("--- AI summary ---")) {
                val idx = existing.notes.indexOf("--- AI summary ---")
                if (idx >= 0) existing.notes.substring(0, idx).trimEnd() + stamp
                else existing.notes + stamp
            } else {
                existing.notes + stamp
            }
            jobDao.insert(
                existing.copy(
                    notes = notes.trim(),
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
            )
            _message.value = "Summary saved into job notes."
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun consumeEstimateDraft(): EstimateDraft? {
        val d = _estimateDraft.value
        _estimateDraft.value = null
        return d
    }

    private suspend fun approximateRoadMiles(
        shop: String,
        dest: String,
        jobLat: Double?,
        jobLng: Double?
    ): Double? {
        val origin = geocodingService.geocode(shop) ?: return null
        val destination = when {
            jobLat != null && jobLng != null &&
                jobLat.isFinite() && jobLng.isFinite() &&
                jobLat in -90.0..90.0 && jobLng in -180.0..180.0 ->
                com.strobingn.wildlifefieldops.data.remote.GeoPoint(jobLat, jobLng)
            else -> geocodingService.geocode(dest)
        } ?: return null
        val straight = haversineMiles(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
        // Simple rural/suburban road factor
        return round(straight * 1.25 * 10.0) / 10.0
    }

    private fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3958.8 // Earth radius miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun friendlyDistanceError(raw: String?): String {
        val msg = raw.orEmpty()
        return when {
            msg.contains("REQUEST_DENIED", ignoreCase = true) ->
                "Could not measure driving miles: Google Distance Matrix is not enabled (or not allowed) for this Maps API key. Enable Distance Matrix API in Google Cloud, or use the approx miles fallback after reinstall. Mileage left at 0 for now."
            msg.contains("OVER_QUERY_LIMIT", ignoreCase = true) ->
                "Maps distance quota hit — mileage left at 0."
            msg.isBlank() -> "Could not measure miles — mileage left at 0."
            else -> "Could not measure miles (${msg.take(80)}) — mileage left at 0."
        }
    }
}
