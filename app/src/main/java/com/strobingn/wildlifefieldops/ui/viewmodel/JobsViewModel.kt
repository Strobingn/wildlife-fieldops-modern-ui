package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.VisitDao
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.Visit
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.GeocodingService
import com.strobingn.wildlifefieldops.data.remote.JobIntakeDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val jobDao: JobDao,
    private val visitDao: VisitDao,
    private val geocodingService: GeocodingService,
    private val aiService: AiService
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow<JobStatus?>(null)
    val selectedStatus = _selectedStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val jobs = combine(_searchQuery, _selectedStatus) { query, status ->
        Pair(query, status)
    }.flatMapLatest { (query, status) ->
        when {
            query.isNotBlank() -> jobDao.search(query)
            status != null -> jobDao.getByStatus(status)
            else -> jobDao.getAll()
        }
    }.onEach { _isLoading.value = false }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount = jobDao.getByStatus(JobStatus.PENDING)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val inProgressCount = jobDao.getByStatus(JobStatus.IN_PROGRESS)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedCount = jobDao.getByStatus(JobStatus.COMPLETED)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalRevenue = jobDao.getByStatus(JobStatus.PAID)
        .map { jobs -> jobs.sumOf { it.actualCost } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setStatusFilter(status: JobStatus?) {
        _selectedStatus.value = status
    }

    fun getJobById(id: String): Flow<Job?> {
        if (id.isBlank() || id == "new") return flowOf(null)
        return jobDao.observeById(id)
    }

    suspend fun loadJobOnce(id: String): Job? {
        if (id.isBlank() || id == "new") return null
        return jobDao.getById(id)
    }

    suspend fun loadScheduledVisits(jobId: String): List<Long> =
        visitDao.getByJobOnce(jobId).filterNot { it.isCompleted }.map { it.visitDate }

    private suspend fun withCoordinates(job: Job): Job {
        if (job.latitude != null && job.longitude != null) return job
        if (job.address.isBlank()) return job
        val point = geocodingService.geocode(job.address) ?: return job
        return job.copy(latitude = point.latitude, longitude = point.longitude)
    }

    fun fillMissingCoordinates() = viewModelScope.launch {
        jobDao.getAllOnce().forEach { job ->
            if ((job.latitude == null || job.longitude == null) && job.address.isNotBlank()) {
                val updated = withCoordinates(job)
                if (updated.latitude != null && updated.longitude != null) {
                    jobDao.insert(
                        updated.copy(
                            updatedAt = System.currentTimeMillis(),
                            isSynced = false
                        )
                    )
                }
            }
        }
    }

    fun saveJob(job: Job) = viewModelScope.launch {
        jobDao.insert(withCoordinates(job.copy(isSynced = false, updatedAt = System.currentTimeMillis())))
    }

    fun updateJob(job: Job) = viewModelScope.launch {
        jobDao.insert(
            withCoordinates(
                job.copy(
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
            )
        )
    }

    fun updateJobDetails(
        jobId: String,
        title: String,
        description: String,
        customerId: String,
        customerName: String,
        address: String,
        type: String,
        priority: com.strobingn.wildlifefieldops.data.model.JobPriority,
        estimatedValue: Double,
        notes: String,
        scheduledDate: Long? = null
    ) = viewModelScope.launch {
        val existing = jobDao.getById(jobId) ?: return@launch
        jobDao.insert(
            withCoordinates(
                existing.copy(
                    title = title,
                    description = description,
                    customerId = customerId,
                    customerName = customerName,
                    address = address,
                    type = com.strobingn.wildlifefieldops.data.model.DefaultServiceTypes.display(type),
                    priority = priority,
                    estimatedValue = estimatedValue,
                    notes = notes,
                    scheduledDate = scheduledDate ?: existing.scheduledDate,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
            )
        )
    }

    fun deleteJob(job: Job) = viewModelScope.launch {
        jobDao.delete(job)
    }

    fun deleteJobById(id: String) = viewModelScope.launch {
        jobDao.deleteById(id)
    }

    fun updateJobStatus(jobId: String, status: JobStatus) = viewModelScope.launch {
        val job = jobDao.getById(jobId)
        job?.let {
            jobDao.update(it.copy(status = status, updatedAt = System.currentTimeMillis(), isSynced = false))
        }
    }

    fun createJob(
        title: String,
        description: String,
        customerId: String,
        customerName: String,
        address: String,
        type: String,
        priority: com.strobingn.wildlifefieldops.data.model.JobPriority,
        estimatedValue: Double,
        scheduledDate: Long?,
        notes: String
    ) = viewModelScope.launch {
        val job = Job(
            title = title,
            description = description,
            customerId = customerId,
            customerName = customerName,
            address = address,
            type = com.strobingn.wildlifefieldops.data.model.DefaultServiceTypes.display(type),
            priority = priority,
            estimatedValue = estimatedValue,
            scheduledDate = scheduledDate,
            notes = notes
        )
        jobDao.insert(withCoordinates(job))
    }

    fun saveJobWithSchedule(
        existingJob: Job?,
        title: String,
        description: String,
        customerId: String,
        customerName: String,
        address: String,
        type: String,
        priority: com.strobingn.wildlifefieldops.data.model.JobPriority,
        estimatedValue: Double,
        notes: String,
        appointmentTimes: List<Long>,
        onSaved: () -> Unit
    ) = viewModelScope.launch {
        val job = (existingJob ?: Job()).copy(
            title = title,
            description = description,
            customerId = customerId,
            customerName = customerName,
            address = address,
            type = com.strobingn.wildlifefieldops.data.model.DefaultServiceTypes.display(type),
            priority = priority,
            estimatedValue = estimatedValue,
            notes = notes,
            scheduledDate = appointmentTimes.minOrNull(),
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        val saved = withCoordinates(job)
        jobDao.insert(saved)
        visitDao.deletePendingForJob(job.id)
        appointmentTimes.distinct().sorted().forEach { scheduledAt ->
            visitDao.insert(
                Visit(
                    jobId = job.id,
                    customerId = job.customerId,
                    customerName = job.customerName,
                    technicianName = job.assignedTo,
                    visitDate = scheduledAt,
                    startTime = scheduledAt
                )
            )
        }
        onSaved()
    }

    private val _aiFillLoading = MutableStateFlow(false)
    val aiFillLoading = _aiFillLoading.asStateFlow()

    private val _aiFillError = MutableStateFlow<String?>(null)
    val aiFillError = _aiFillError.asStateFlow()

    private val _aiFillSource = MutableStateFlow<String?>(null)
    val aiFillSource = _aiFillSource.asStateFlow()

    fun clearAiFillError() {
        _aiFillError.value = null
    }

    fun fillJobFromDictation(
        transcript: String,
        onFilled: (JobIntakeDraft) -> Unit
    ) {
        if (_aiFillLoading.value) return
        val text = transcript.trim()
        if (text.isBlank()) {
            _aiFillError.value = "Dictate job details first, then tap AI Fill Job."
            return
        }
        _aiFillLoading.value = true
        _aiFillError.value = null
        _aiFillSource.value = null
        viewModelScope.launch {
            val result = aiService.parseJobFromDictation(text)
            _aiFillLoading.value = false
            if (result.draft != null) {
                _aiFillSource.value = result.sourceLabel
                onFilled(result.draft)
            } else {
                _aiFillError.value = result.error ?: "AI job fill failed."
            }
        }
    }


}
