package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.ReminderDao
import com.strobingn.wildlifefieldops.data.local.VisitDao
import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class DaySchedule(
    val date: Long,
    val jobs: List<Job>,
    val reminders: List<Reminder>
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val jobDao: JobDao,
    private val reminderDao: ReminderDao,
    private val visitDao: VisitDao,
    private val inspectionDao: InspectionDao
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate = _selectedDate.asStateFlow()

    val allJobs = jobDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReminders = reminderDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVisits = visitDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInspections = inspectionDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedDate(date: Long) {
        _selectedDate.value = date
    }

    fun getJobsForDate(date: Long): Flow<List<Job>> = allJobs.map { jobs ->
        val cal = Calendar.getInstance().apply { timeInMillis = date }
        val dayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val dayEnd = dayStart + 86400000L
        jobs.filter { it.scheduledDate != null && it.scheduledDate in dayStart until dayEnd }
    }

    fun getJobsForMonth(year: Int, month: Int): Flow<List<Job>> = allJobs.map { jobs ->
        val cal = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0) }
        val monthStart = cal.timeInMillis
        val monthEnd = cal.apply { add(Calendar.MONTH, 1) }.timeInMillis
        jobs.filter { it.scheduledDate != null && it.scheduledDate in monthStart until monthEnd }
    }

    fun completeReminder(reminderId: String) = viewModelScope.launch {
        val reminders = allReminders.value
        val reminder = reminders.find { it.id == reminderId }
        reminder?.let {
            reminderDao.update(it.copy(
                status = com.strobingn.wildlifefieldops.data.model.ReminderStatus.COMPLETED,
                completedDate = System.currentTimeMillis()
            ))
        }
    }

    fun addReminder(reminder: Reminder) = viewModelScope.launch {
        reminderDao.insert(reminder)
    }
}
