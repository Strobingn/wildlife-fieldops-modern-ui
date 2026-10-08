package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.FindingSeverity
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ai.fieldops.ScheduledInspections
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.InspectionsViewModel
import java.text.SimpleDateFormat
import java.util.*

data class InspectionListPreview(
    val inspections: List<Inspection>,
    val inspectionCount: Int = inspections.size,
    val followUpCount: Int = inspections.count { it.followUpRequired },
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionListScreen(
    onNavigateToInspectionDetail: (String) -> Unit,
    onNavigateToInspectionForm: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true,
    onScheduleInspection: () -> Unit = {},
    onOpenScheduledInspection: (String) -> Unit = {},
    preview: InspectionListPreview? = null
) {
    if (preview != null) {
        InspectionListContent(
            inspections = preview.inspections,
            inspectionCount = preview.inspectionCount,
            followUpCount = preview.followUpCount,
            searchQuery = preview.searchQuery,
            isLoading = preview.isLoading,
            onSearch = {},
            onNavigateToInspectionDetail = onNavigateToInspectionDetail,
            onNavigateToInspectionForm = onNavigateToInspectionForm,
            onBack = onBack,
            showBack = showBack,
            scheduled = emptyList(),
            onScheduleInspection = onScheduleInspection,
            onOpenScheduledInspection = onOpenScheduledInspection
        )
        return
    }
    val viewModel: InspectionsViewModel = hiltViewModel()
    val inspections by viewModel.inspections.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val inspectionCount by viewModel.inspectionCount.collectAsState()
    val followUpCount by viewModel.followUpCount.collectAsState()
    val allJobs by viewModel.allJobs.collectAsState()
    val scheduled = remember(allJobs) { ScheduledInspections.list(allJobs) }
    InspectionListContent(
        inspections = inspections,
        inspectionCount = inspectionCount,
        followUpCount = followUpCount,
        searchQuery = searchQuery,
        isLoading = isLoading,
        onSearch = viewModel::setSearchQuery,
        onNavigateToInspectionDetail = onNavigateToInspectionDetail,
        onNavigateToInspectionForm = onNavigateToInspectionForm,
        onBack = onBack,
        showBack = showBack,
        scheduled = scheduled,
        onScheduleInspection = onScheduleInspection,
        onOpenScheduledInspection = onOpenScheduledInspection
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectionListContent(
    inspections: List<Inspection>,
    inspectionCount: Int,
    followUpCount: Int,
    searchQuery: String,
    isLoading: Boolean,
    onSearch: (String) -> Unit,
    onNavigateToInspectionDetail: (String) -> Unit,
    onNavigateToInspectionForm: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean,
    scheduled: List<Job>,
    onScheduleInspection: () -> Unit,
    onOpenScheduledInspection: (String) -> Unit
) {
    // 0 = Scheduled (inspections only, opens first), 1 = Reports.
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inspections", color = TextPrimary) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = if (tab == 0) onScheduleInspection else onNavigateToInspectionForm,
                containerColor = if (tab == 0) AccentBlue else PrimaryGreen,
                contentColor = OnPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = if (tab == 0) "Schedule inspection" else "New inspection report")
            }
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = BackgroundDark,
                contentColor = TextPrimary
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Scheduled (${scheduled.size})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Reports ($inspectionCount)") }
                )
            }
            if (tab == 0) {
                ScheduledInspectionsPane(
                    scheduled = scheduled,
                    onScheduleInspection = onScheduleInspection,
                    onOpen = onOpenScheduledInspection
                )
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearch,
                    placeholder = { Text("Search inspections...", color = TextTertiary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BackgroundCard,
                        unfocusedContainerColor = BackgroundCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountSummaryCell(
                        label = "Inspections",
                        count = inspectionCount,
                        modifier = Modifier.weight(1f)
                    )
                    CountSummaryCell(
                        label = "Follow-ups",
                        count = followUpCount,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isLoading) {
                    ListShimmer(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (inspections.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    },
                                    title = if (searchQuery.isBlank()) "No inspections yet" else "No matches",
                                    subtitle = if (searchQuery.isBlank()) "Tap + to create, or open Inspect from a Job" else "Try a different search",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            itemsIndexed(inspections, key = { _, insp -> insp.id }) { index, inspection ->
                                FadeSlideIn(index = index) {
                                    InspectionListItem(
                                        inspection = inspection,
                                        onClick = { onNavigateToInspectionDetail(inspection.id) }
                                    )
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

/** Only scheduled inspections, grouped by day, with their own search. */
@Composable
private fun ScheduledInspectionsPane(
    scheduled: List<Job>,
    onScheduleInspection: () -> Unit,
    onOpen: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(scheduled, query) { scheduled.filter { ScheduledInspections.matches(it, query) } }
    val sections = remember(shown) { ScheduledInspections.sections(shown) }
    val todayCount = sections.firstOrNull { it.label == "Today" }?.jobs?.size ?: 0
    val needsDecision = sections.firstOrNull { it.label.startsWith("Past") }?.jobs?.size ?: 0

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search name, address...", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = BorderDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = BackgroundCard,
                unfocusedContainerColor = BackgroundCard
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CountSummaryCell(label = "Scheduled", count = scheduled.size, modifier = Modifier.weight(1f))
            CountSummaryCell(label = "Today", count = todayCount, modifier = Modifier.weight(1f))
            CountSummaryCell(label = "Need a decision", count = needsDecision, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sections.isEmpty()) {
                item {
                    EmptyState(
                        icon = {
                            Icon(
                                Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        },
                        title = if (query.isBlank()) "No scheduled inspections" else "No matches",
                        subtitle = if (query.isBlank()) "Tap + to schedule one. It goes on your Schedule like a job." else "Try a different search",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isBlank()) {
                    item {
                        Button(
                            onClick = onScheduleInspection,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = OnPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Schedule inspection") }
                    }
                }
            }
            sections.forEach { section ->
                item(key = "day-${section.label}") {
                    Text(
                        "${section.label} (${section.jobs.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (section.label.startsWith("Past")) AccentOrange else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(section.jobs, key = { "scheduled-${it.id}" }) { job ->
                    JobListItem(job = job, onClick = { onOpen(job.id) })
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun InspectionListItem(inspection: Inspection, onClick: () -> Unit) {
    val severityColor = when (inspection.severity) {
        FindingSeverity.NONE -> TextSecondary
        FindingSeverity.LOW -> PrimaryGreen
        FindingSeverity.MODERATE -> StatusPending
        FindingSeverity.HIGH -> ErrorRed
        FindingSeverity.CRITICAL -> StatusUrgent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        inspection.customerName.ifBlank { "Unknown Customer" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        inspection.inspectionType.name.lowercase().replaceFirstChar { it.uppercase() } + " Inspection",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (inspection.jobId.isNotBlank()) {
                        Text(
                            "Job · " + inspection.jobId.take(8) + "…",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentBlue
                        )
                    }
                }
                if (inspection.severity != FindingSeverity.NONE) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(severityColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            inspection.severity.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = severityColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(inspection.inspectionDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Default.Person, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    inspection.inspectorName.ifBlank { "Unassigned" },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            if (inspection.speciesIdentified.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                SpeciesChip(species = inspection.speciesIdentified)
            }

            if (inspection.followUpRequired) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FollowTheSigns, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Follow-up required",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange
                    )
                }
            }
        }
    }
}
