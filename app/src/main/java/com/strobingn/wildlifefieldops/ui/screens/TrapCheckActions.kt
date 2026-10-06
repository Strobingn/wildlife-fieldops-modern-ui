package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckItem
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckOutcome
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckPlanner
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckRecorder
import com.strobingn.wildlifefieldops.ai.fieldops.TrapReminders
import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.StatusUrgent
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/** Wall clock that ticks every 30 seconds so "Due 3:40 PM" flips to "Due now" on time. */
@Composable
internal fun rememberMinuteTicker(fixedNow: Long? = null): Long {
    var now by remember { mutableLongStateOf(fixedNow ?: System.currentTimeMillis()) }
    if (fixedNow == null) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                now = System.currentTimeMillis()
            }
        }
    }
    return fixedNow ?: now
}

/** "Due now", "Due 3:40 PM", or the old day label when a trap has no due time. */
internal fun trapDueText(item: TrapCheckItem, now: Long): String =
    if (item.trap.nextCheckDate == null) TrapCheckPlanner.dueLabel(item.dueState)
    else TrapReminders.dueText(item.trap.nextCheckDate, now)

@Composable
internal fun TrapCheckCard(
    item: TrapCheckItem,
    now: Long,
    defaultIntervalHours: Int,
    onOpenJob: () -> Unit,
    onLog: () -> Unit,
    onChecked: () -> Unit,
    onPulled: () -> Unit
) {
    val dueText = trapDueText(item, now)
    val dueNow = item.trap.nextCheckDate?.let { it <= now } == true
    val dueColor = if (dueNow) StatusUrgent else TextSecondary
    val active = TrapCheckPlanner.isActive(item.trap.status)
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trap-card-${item.trap.id}")
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PestControl, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    item.trap.trapId.ifBlank { "Trap" } + " · " + item.trap.status.name.replace('_', ' '),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    dueText,
                    color = dueColor,
                    fontWeight = if (dueNow) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (item.jobTitle.isNotBlank()) Text(item.jobTitle, color = TextSecondary)
            if (item.trap.trapLocation.isNotBlank()) {
                Text(item.trap.trapLocation, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            }
            val interval = TrapReminders.intervalHours(item.trap, defaultIntervalHours)
            val lastCheck = item.trap.checkDate.takeIf { it > 0L }?.let {
                " · last checked " + java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(java.util.Date(it))
            }.orEmpty()
            Text(
                "Check every $interval h$lastCheck",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
            if (item.trap.catchType != CatchType.NONE) {
                Text("Catch: ${item.trap.catchType.name} ×${item.trap.catchCount}", color = TextSecondary)
            }
            if (active) {
                Button(
                    onClick = onChecked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("trap-checked-${item.trap.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Checked", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onLog) { Text("Log check", color = TextPrimary) }
                if (active) {
                    OutlinedButton(onClick = onPulled) { Text("Pulled", color = TextPrimary) }
                }
                TextButton(onClick = onOpenJob, enabled = item.trap.jobId.isNotBlank()) {
                    Text("Open job", color = PrimaryGreen)
                }
            }
        }
    }
}

/** The optional note for the Checked button. Saving records the time even with no note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrapCheckedDialog(
    trap: TrapLog,
    onDismiss: () -> Unit,
    onSave: (outcome: TrapCheckOutcome, species: CatchType, count: Int, note: String) -> Unit
) {
    var outcome by remember { mutableStateOf(TrapCheckOutcome.NONE) }
    var species by remember { mutableStateOf(CatchType.NONE) }
    var count by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Checked ${trap.trapId.ifBlank { "trap" }}", color = TextPrimary) },
        text = {
            TrapCheckedForm(
                outcome = outcome,
                onOutcome = { outcome = it },
                species = species,
                onSpecies = { species = it },
                count = count,
                onCount = { count = it },
                note = note,
                onNote = { note = it },
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = if (outcome == TrapCheckOutcome.CAUGHT && species == CatchType.NONE) CatchType.RACCOON else species
                    onSave(outcome, chosen, count.toIntOrNull() ?: 1, note)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) { Text("Save check") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

/** Body of the Checked dialog: outcome chips, species and count for a catch, and an optional note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrapCheckedForm(
    outcome: TrapCheckOutcome,
    onOutcome: (TrapCheckOutcome) -> Unit,
    species: CatchType,
    onSpecies: (CatchType) -> Unit,
    count: String,
    onCount: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Saves the time now. A note is optional.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrapCheckOutcome.entries.forEach { option ->
                FilterChip(
                    selected = outcome == option,
                    onClick = { onOutcome(option) },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
        if (outcome == TrapCheckOutcome.CAUGHT) {
            EnumPicker("Species", CatchType.entries.filter { it != CatchType.NONE }, species.takeIf { it != CatchType.NONE } ?: CatchType.RACCOON) {
                onSpecies(it)
            }
            OutlinedTextField(
                value = count,
                onValueChange = { onCount(it.filter { ch -> ch.isDigit() }) },
                label = { Text("How many") },
                modifier = Modifier.fillMaxWidth(),
                colors = trapFieldColors()
            )
        }
        OutlinedTextField(
            value = note,
            onValueChange = onNote,
            label = { Text("Note (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
            colors = trapFieldColors()
        )
    }
}

@Composable
internal fun TrapPulledDialog(trap: TrapLog, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Pull ${trap.trapId.ifBlank { "this trap" }}?", color = TextPrimary) },
        text = {
            Text(
                "It comes off the check list and gets no more reminders. The trap record stays on the job.",
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) { Text("Pulled") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
internal fun DecLogOfferDialog(trap: TrapLog, onDismiss: () -> Unit, onAdd: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Add to DEC log?", color = TextPrimary) },
        text = {
            Text(
                "Start a new DEC log entry from this catch (" +
                    TrapCheckRecorder.speciesLabel(trap.catchType) + " ×" + trap.catchCount +
                    "). Only empty cells are filled. Nothing is added unless you tap Add.",
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) { Text("Add to DEC log") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now", color = TextSecondary) } }
    )
}

/** Compact Home block: trap checks due today, one line each. */
@Composable
internal fun HomeTrapChecksCard(
    items: List<TrapCheckItem>,
    now: Long,
    onOpen: () -> Unit,
    maxRows: Int = 3
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-trap-checks"),
        shape = com.strobingn.wildlifefieldops.ui.theme.FieldShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onOpen
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PestControl,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Trap checks due today",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    items.size.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items.take(maxRows).forEach { item ->
                val due = trapDueText(item, now)
                val dueNow = item.trap.nextCheckDate?.let { it <= now } == true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        listOf(item.trap.trapId.ifBlank { "Trap" }, item.jobTitle)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        due,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (dueNow) FontWeight.Bold else FontWeight.Medium,
                        color = if (dueNow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            val extra = items.size - maxRows
            if (extra > 0) {
                Text(
                    "+$extra more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun trapFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
