package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.fieldops.DecLogExporter
import com.strobingn.wildlifefieldops.ai.fieldops.FollowUpKind
import com.strobingn.wildlifefieldops.ai.fieldops.FollowUpPlanner
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckItem
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckPlanner
import com.strobingn.wildlifefieldops.ai.fieldops.TrapFieldOpsStore
import com.strobingn.wildlifefieldops.ai.fieldops.WeatherAdviceDraft
import com.strobingn.wildlifefieldops.ai.fieldops.WeatherAdviceInput
import com.strobingn.wildlifefieldops.ai.fieldops.WeatherTrapAdvice
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.TrapLogDao
import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import com.strobingn.wildlifefieldops.data.remote.WeatherSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrapCheckViewModel @Inject constructor(
    private val trapLogDao: TrapLogDao,
    private val jobDao: JobDao,
    private val trapFieldOpsStore: TrapFieldOpsStore
) : ViewModel() {

    val jobs: StateFlow<List<Job>> = jobDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val traps: StateFlow<List<TrapLog>> = trapLogDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueToday: StateFlow<List<TrapCheckItem>> = combine(traps, jobs) { trapRows, jobRows ->
        val titles = jobRows.associate { it.id to it.title.ifBlank { it.customerName } }
        TrapCheckPlanner.todaysList(trapRows).map { item ->
            item.copy(jobTitle = titles[item.trap.jobId].orEmpty())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scheduled: StateFlow<List<TrapCheckItem>> = combine(traps, jobs) { trapRows, jobRows ->
        val titles = jobRows.associate { it.id to it.title.ifBlank { it.customerName } }
        TrapCheckPlanner.allScheduled(trapRows).map { item ->
            item.copy(jobTitle = titles[item.trap.jobId].orEmpty())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _lastAdvice = MutableStateFlow<WeatherAdviceDraft?>(null)
    val lastAdvice: StateFlow<WeatherAdviceDraft?> = _lastAdvice

    init {
        viewModelScope.launch {
            jobDao.getAllOnce().forEach { trapFieldOpsStore.hydrateFromJob(it) }
        }
    }

    fun trapsForJob(jobId: String): List<TrapLog> = traps.value.filter { it.jobId == jobId }

    fun saveTrap(trap: TrapLog) = viewModelScope.launch {
        trapFieldOpsStore.saveTrap(trap)
        _message.value = "Trap saved — pending cloud sync on the job."
    }

    fun deleteTrap(trap: TrapLog) = viewModelScope.launch {
        trapFieldOpsStore.deleteTrap(trap)
        _message.value = "Trap removed."
    }

    fun logCheck(
        trap: TrapLog,
        status: TrapStatus,
        catchType: CatchType,
        catchCount: Int,
        baitType: String,
        disposition: String,
        method: String,
        notes: String,
        nextCheckDate: Long?
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        trapFieldOpsStore.saveTrap(
            trap.copy(
                status = status,
                catchType = catchType,
                catchCount = catchCount,
                baitType = baitType,
                disposition = disposition,
                method = method,
                conditionNotes = notes,
                checkDate = now,
                nextCheckDate = nextCheckDate ?: TrapCheckPlanner.nextCheckAfter(status, now),
                isSynced = false,
                updatedAt = now
            )
        )
        _message.value = "Check logged."
    }

    fun draftWeatherAdvice(job: Job?, snap: WeatherSnapshot?): WeatherAdviceDraft {
        val jobTraps = job?.id?.let { id -> traps.value.filter { it.jobId == id } } ?: traps.value
        val draft = WeatherTrapAdvice.suggest(
            WeatherAdviceInput(
                condition = snap?.condition.orEmpty(),
                description = snap?.description.orEmpty(),
                tempF = snap?.tempF,
                windMph = snap?.windMph,
                humidity = snap?.humidity,
                species = job?.confirmedSpecies.orEmpty().ifBlank { job?.type.orEmpty() },
                trapCount = jobTraps.count { TrapCheckPlanner.isActive(it.status) },
                hasCatch = jobTraps.any { it.catchType != CatchType.NONE || it.catchCount > 0 }
            )
        )
        _lastAdvice.value = draft
        return draft
    }

    fun acceptWeatherAdvice(jobId: String, advice: String, source: String) = viewModelScope.launch {
        trapFieldOpsStore.saveWeatherAdvice(jobId, advice, source)
        _message.value = "Weather advice saved on the job."
    }

    fun createFollowUp(job: Job, kind: FollowUpKind, dueAt: Long, notes: String) = viewModelScope.launch {
        trapFieldOpsStore.createFollowUp(job, kind, dueAt, notes)
        _message.value = "Follow-up visit and reminder created."
    }

    fun suggestFollowUp(job: Job) = FollowUpPlanner.suggest(
        com.strobingn.wildlifefieldops.ai.fieldops.FollowUpInput(
            status = job.status.name,
            species = job.confirmedSpecies.ifBlank { job.type },
            jobType = job.type,
            notes = job.notes + " " + job.followUpNotes,
            completedAt = job.completedDate,
            hasActiveTraps = traps.value.any { it.jobId == job.id && TrapCheckPlanner.isActive(it.status) }
        )
    )

    fun decCsv(jobId: String? = null): String {
        val rows = traps.value.filter { jobId == null || it.jobId == jobId }
        val jobsById = jobs.value.associateBy { it.id }
        return DecLogExporter.toCsv(DecLogExporter.rowsFromTraps(rows, jobsById))
    }

    fun clearMessage() {
        _message.value = null
    }
}
