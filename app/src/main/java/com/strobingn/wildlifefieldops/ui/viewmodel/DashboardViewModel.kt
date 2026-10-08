package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.*
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.ui.screens.OpenHomeJobs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardStats(
    val totalJobs: Int = 0,
    val scheduledJobs: Int = 0,
    val inProgressJobs: Int = 0,
    val completedJobs: Int = 0,
    val totalCustomers: Int = 0,
    val totalInspections: Int = 0,
    val followUpRequired: Int = 0,
    val todayJobs: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val jobDao: JobDao,
    private val customerDao: CustomerDao,
    private val inspectionDao: InspectionDao,
    private val reminderDao: ReminderDao,
    private val trapLogDao: TrapLogDao
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // One shared jobs query for every card below instead of six separate full-table queries.
    private val allJobsFlow = jobDao.getAll()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val stats: StateFlow<DashboardStats> = combine(
        allJobsFlow,
        customerDao.getAll(),
        inspectionDao.getAll(),
        reminderDao.getPending()
    ) { allJobs, customers, inspections, reminders ->
        val jobs = allJobs.filterNot { com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger.isLedger(it) }
        val now = System.currentTimeMillis()
        val dayStart = LocalPeriods.dayStart(now)
        val dayEnd = LocalPeriods.nextDayStart(now)

        DashboardStats(
            totalJobs = jobs.size,
            scheduledJobs = jobs.count {
                com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.flag(it.status) == JobStatus.SCHEDULED
            },
            inProgressJobs = jobs.count {
                com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.flag(it.status) == JobStatus.IN_PROGRESS
            },
            completedJobs = jobs.count {
                com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.flag(it.status) == JobStatus.COMPLETED
            },
            totalCustomers = customers.size,
            totalInspections = inspections.size,
            followUpRequired = inspections.count { it.followUpRequired },
            todayJobs = jobs.count {
                it.scheduledDate != null && it.scheduledDate in dayStart until dayEnd
            }
        )
    }.flowOn(Dispatchers.Default)
    .onEach { _isLoading.value = false }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    val openJobs = allJobsFlow
        .map { OpenHomeJobs.list(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayOnSchedule = allJobsFlow
        .map { list ->
            val jobs = list.filterNot { com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger.isLedger(it) }
            val now = System.currentTimeMillis()
            val dayStart = LocalPeriods.dayStart(now)
            val dayEnd = LocalPeriods.nextDayStart(now)
            jobs.filter {
                it.scheduledDate != null && it.scheduledDate in dayStart until dayEnd
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentJobs = allJobsFlow
        .map { list -> list.filterNot { com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger.isLedger(it) }.take(5) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingReminders = reminderDao.getPending()
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueNextSteps = allJobsFlow
        .map { jobs ->
            val now = System.currentTimeMillis()
            jobs.filter { it.nextStep.isNotBlank() }
                .sortedBy { it.nextStepDueAt ?: Long.MAX_VALUE }
                .filter { step ->
                    val due = step.nextStepDueAt
                    due == null || due <= now + 2 * 86_400_000L
                }
                .take(5)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueTrapChecks = combine(trapLogDao.getAll(), allJobsFlow) { traps, jobs ->
        val titles = jobs.associate { it.id to it.title.ifBlank { it.customerName } }
        com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckPlanner.todaysList(traps)
            .map { item -> item.copy(jobTitle = titles[item.trap.jobId].orEmpty()) }
            .take(5)
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
