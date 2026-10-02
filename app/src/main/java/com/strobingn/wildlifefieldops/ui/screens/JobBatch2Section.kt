package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.FieldDate
import com.strobingn.wildlifefieldops.ai.fieldops.FollowUpKind
import com.strobingn.wildlifefieldops.ai.fieldops.FollowUpPlanner
import com.strobingn.wildlifefieldops.ai.fieldops.ManualField
import com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.pricing.isManual
import com.strobingn.wildlifefieldops.ui.components.ApplySuggestionChip
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.TrapCheckViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.WeatherUiState
import com.strobingn.wildlifefieldops.util.DecLogShare
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JobBatch2Section(
    job: Job,
    traps: List<TrapLog>,
    weatherState: WeatherUiState,
    trapVm: TrapCheckViewModel,
    onOpenTrapChecks: () -> Unit
) {
    val context = LocalContext.current
    var showAddTrap by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TrapLog?>(null) }
    var followKind by remember(job.id, job.followUpKind) {
        mutableStateOf(FollowUpPlanner.parseKind(job.followUpKind))
    }
    var followNotes by remember(job.id, job.followUpNotes) { mutableStateOf(job.followUpNotes) }
    var followDueText by remember(job.id, job.followUpDueAt) {
        mutableStateOf(job.followUpDueAt?.let { FieldDate.formatDay(it) }.orEmpty())
    }
    var followNotesPreview by remember(job.id) { mutableStateOf<String?>(null) }
    var followDateError by remember { mutableStateOf<String?>(null) }
    val lastAdvice by trapVm.lastAdvice.collectAsState()

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Trap checks", color = TextPrimary, fontWeight = FontWeight.Medium)
            if (traps.isEmpty()) {
                Text("No traps on this job yet. Add one or drop a pin on the map.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            } else {
                traps.forEach { trap ->
                    TextButton(onClick = { editing = trap }) {
                        Text(
                            "${trap.trapId.ifBlank { "Trap" }} · ${trap.status.name.replace('_', ' ')} · ${trap.trapLocation.ifBlank { "no location" }}",
                            color = TextSecondary
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showAddTrap = true }) {
                    androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Add trap")
                }
                OutlinedButton(onClick = onOpenTrapChecks) {
                    androidx.compose.material3.Icon(Icons.Default.PestControl, contentDescription = null)
                    Text(" Daily list")
                }
            }
        }
    }

    WeatherAdviceCard(
        weatherState = weatherState,
        savedAdvice = job.weatherTrapAdvice,
        adviceManual = job.pricing.isManual(ManualField.WEATHER),
        draft = lastAdvice,
        onSuggest = {
            val snap = (weatherState as? WeatherUiState.Ready)?.snap
            trapVm.draftWeatherAdvice(job, snap).text
        },
        onAccept = { text -> trapVm.acceptWeatherAdvice(job.id, text, "heuristic") }
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NY DEC nuisance log", color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(
                "Required fields: species, date, location, disposition, method. Export CSV to share or file.",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { DecLogShare.shareCsv(context, trapVm.decCsv(job.id), "ny-dec-${job.id.take(8)}.csv") },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) {
                androidx.compose.material3.Icon(Icons.Default.Share, contentDescription = null)
                Text("  Export this job")
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Follow-up visit", color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(
                "Creates a dated visit + reminder. AI suggests; you edit.",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FollowUpKind.entries.forEach { kind ->
                    val selected = followKind == kind
                    if (selected) {
                        Button(
                            onClick = { followKind = kind },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                        ) { Text(FollowUpPlanner.label(kind)) }
                    } else {
                        OutlinedButton(onClick = { followKind = kind }) { Text(FollowUpPlanner.label(kind)) }
                    }
                }
            }
            if (followKind == null) {
                Text("No kind selected. Save stores a blank kind.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = followNotes,
                onValueChange = { followNotes = it },
                label = { Text("Follow-up notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            OutlinedTextField(
                value = followDueText,
                onValueChange = {
                    followDueText = it
                    followDateError = null
                },
                label = { Text("Due date (yyyy-MM-dd)") },
                supportingText = {
                    Text(followDateError ?: "Blank clears the due date.", color = if (followDateError != null) com.strobingn.wildlifefieldops.ui.theme.StatusUrgent else TextTertiary)
                },
                isError = followDateError != null,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            job.followUpDueAt?.let {
                Text(
                    "Saved: ${SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(it))}",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val draft = trapVm.suggestFollowUp(job)
                    val notesBefore = followNotes
                    val filled = FollowUpPlanner.fillBlanks(followKind, followNotes, followDueText, draft, FieldDate::formatDay)
                    followKind = filled.first
                    followNotes = filled.second
                    followDueText = filled.third
                    followDateError = null
                    followNotesPreview = if (notesBefore.isBlank()) {
                        null
                    } else {
                        OperatorWins.preview(notesBefore, draft.notes, manual = true)
                    }
                }) {
                    androidx.compose.material3.Icon(Icons.Default.Event, contentDescription = null)
                    Text(" Suggest")
                }
                Button(
                    onClick = {
                        val due = FieldDate.parseDay(followDueText)
                        if (!due.ok) {
                            followDateError = due.error
                            return@Button
                        }
                        followDateError = null
                        trapVm.createFollowUp(job, followKind, due.millis, followNotes.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Save visit + reminder") }
                TextButton(onClick = {
                    followKind = null
                    followNotes = ""
                    followDueText = ""
                    followDateError = null
                    trapVm.createFollowUp(job, null, null, "")
                }) { Text("Clear") }
            }
            ApplySuggestionChip(followNotesPreview) {
                followNotes = it
                followNotesPreview = null
            }
        }
    }

    if (showAddTrap) {
        TrapEditorDialog(
            jobs = listOf(job),
            initial = TrapLog(jobId = job.id, latitude = job.latitude, longitude = job.longitude, trapLocation = job.address),
            onDismiss = { showAddTrap = false },
            onSave = {
                trapVm.saveTrap(it.copy(jobId = job.id))
                showAddTrap = false
            }
        )
    }
    editing?.let { trap ->
        TrapEditorDialog(
            jobs = listOf(job),
            initial = trap,
            onDismiss = { editing = null },
            onSave = {
                trapVm.saveTrap(it.copy(jobId = job.id))
                editing = null
            },
            onDelete = {
                trapVm.deleteTrap(trap)
                editing = null
            }
        )
    }
}
