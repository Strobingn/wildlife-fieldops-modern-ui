package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateToJobDetail: (String) -> Unit,
    onNavigateToJobForm: () -> Unit,
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val allJobs by viewModel.allJobs.collectAsState()
    val allVisits by viewModel.allVisits.collectAsState()
    val allInspections by viewModel.allInspections.collectAsState()
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    val dayVisits by remember(selectedDate, allVisits) {
        derivedStateOf {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
            val dayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val dayEnd = dayStart + 86400000L
            allVisits.filter { !it.isCompleted && it.visitDate in dayStart until dayEnd }
                .sortedBy { it.visitDate }
        }
    }
    val dayInspections by remember(selectedDate, allInspections) {
        derivedStateOf {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
            val dayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val dayEnd = dayStart + 86400000L
            allInspections.filter { it.inspectionDate in dayStart until dayEnd }
                .sortedBy { it.inspectionDate }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToJobForm,
                containerColor = PrimaryGreen,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Job")
            }
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Month Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    currentMonth = Calendar.getInstance().apply {
                        timeInMillis = currentMonth.timeInMillis
                        add(Calendar.MONTH, -1)
                    }
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = TextPrimary)
                }
                Text(
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonth.time),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    currentMonth = Calendar.getInstance().apply {
                        timeInMillis = currentMonth.timeInMillis
                        add(Calendar.MONTH, 1)
                    }
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = TextPrimary)
                }
            }

            // Calendar Grid
            CalendarGrid(
                month = currentMonth,
                scheduledTimes = allVisits.filterNot { it.isCompleted }.map { it.visitDate } +
                    allInspections.map { it.inspectionDate },
                selectedDate = selectedDate,
                onDateSelected = { viewModel.setSelectedDate(it) }
            )

            // Day's Jobs
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Schedule for ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(selectedDate))}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (dayVisits.isEmpty() && dayInspections.isEmpty()) {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = "Nothing scheduled",
                    subtitle = "Only jobs and inspections for this day appear here",
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val jobsById = allJobs.associateBy { it.id }
                dayVisits.forEachIndexed { index, visit ->
                    FadeSlideIn(index = index) {
                        ScheduleAgendaCard(
                            time = visit.visitDate,
                            label = "JOB VISIT",
                            title = jobsById[visit.jobId]?.title ?: visit.customerName.ifBlank { "Scheduled job" },
                            subtitle = jobsById[visit.jobId]?.address.orEmpty(),
                            onClick = { if (visit.jobId.isNotBlank()) onNavigateToJobDetail(visit.jobId) }
                        )
                    }
                }
                dayInspections.forEachIndexed { index, inspection ->
                    FadeSlideIn(index = dayVisits.size + index) {
                        ScheduleAgendaCard(
                            time = inspection.inspectionDate,
                            label = "INSPECTION",
                            title = inspection.customerName.ifBlank { "Scheduled inspection" },
                            subtitle = inspection.inspectionType.name.lowercase().replaceFirstChar { it.uppercase() },
                            onClick = { if (inspection.jobId.isNotBlank()) onNavigateToJobDetail(inspection.jobId) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CalendarGrid(
    month: Calendar,
    scheduledTimes: List<Long>,
    selectedDate: Long,
    onDateSelected: (Long) -> Unit
) {
    val cal = month.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = Calendar.getInstance()

    val dayOfWeekNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Day headers
        Row(modifier = Modifier.fillMaxWidth()) {
            dayOfWeekNames.forEach { dayName ->
                Text(
                    dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Calendar days
        var day = 1
        for (week in 0..5) {
            if (day > daysInMonth) break
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (dow in 0..6) {
                    if ((week == 0 && dow < firstDayOfWeek) || day > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val thisCal = Calendar.getInstance().apply {
                            timeInMillis = cal.timeInMillis
                            set(Calendar.DAY_OF_MONTH, day)
                        }
                        val thisDayStart = thisCal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                        val thisDayEnd = thisDayStart + 86400000L
                        val hasJobs = scheduledTimes.any { it in thisDayStart until thisDayEnd }
                        val isSelected = selectedDate in thisDayStart until thisDayEnd
                        val isToday = today.get(Calendar.YEAR) == thisCal.get(Calendar.YEAR) &&
                                     today.get(Calendar.DAY_OF_YEAR) == thisCal.get(Calendar.DAY_OF_YEAR)

                        CalendarDayCell(
                            day = day,
                            hasJobs = hasJobs,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = { onDateSelected(thisDayStart) },
                            modifier = Modifier.weight(1f)
                        )
                        day++
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ScheduleAgendaCard(
    time: Long,
    label: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BackgroundCard)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                SimpleDateFormat("h:mm\na", Locale.getDefault()).format(Date(time)),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    hasJobs: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> PrimaryGreen
                    isToday -> PrimaryGreen.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            day.toString(),
            color = when {
                isSelected -> Color.Black
                isToday -> PrimaryGreen
                else -> TextPrimary
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (hasJobs && !isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentBlue)
            )
        }
    }
}
