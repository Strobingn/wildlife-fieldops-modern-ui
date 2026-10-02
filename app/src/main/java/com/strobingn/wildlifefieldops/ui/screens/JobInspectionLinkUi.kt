package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.data.inspection.JobInspectionLink
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JobLinkedInspectionsCard(
    job: Job,
    inspections: List<Inspection>,
    onOpen: (String) -> Unit,
    onUnlink: (String) -> Unit,
    onLink: (String) -> Unit,
    onNew: () -> Unit
) {
    val linked = remember(inspections, job.id) { JobInspectionLink.linkedTo(job.id, inspections) }
    val suggested = remember(inspections, job.id, job.customerId, job.customerName, job.address) {
        JobInspectionLink.suggestedForJob(job, inspections)
    }
    var showPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Inspections", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if (linked.isEmpty()) {
                Text("None linked yet.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            } else {
                linked.forEach { insp ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                insp.customerName.ifBlank { insp.inspectionType.name.replace('_', ' ') },
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                dateFmt.format(Date(insp.inspectionDate)) +
                                    insp.speciesIdentified.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                color = TextTertiary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = { onOpen(insp.id) }) { Text("Open", color = PrimaryGreen) }
                        TextButton(onClick = { onUnlink(insp.id) }) { Text("Unlink", color = TextSecondary) }
                    }
                }
            }
            suggested.firstOrNull()?.let { hint ->
                Text(
                    "Suggested (same customer/address): ${hint.customerName.ifBlank { "inspection" }} — tap Link to attach. Never auto-linked.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
                TextButton(onClick = { onLink(hint.id) }) {
                    Text("Link suggested inspection", color = PrimaryGreen)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("Link inspection")
                }
                OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f)) {
                    Text("New inspection for this job")
                }
            }
        }
    }

    if (showPicker) {
        val choices = JobInspectionLink.rankPicker(job, inspections)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            containerColor = BackgroundCard,
            title = { Text("Link inspection", color = TextPrimary) },
            text = {
                Column {
                    if (choices.isEmpty()) {
                        Text("No inspections on this phone yet.", color = TextSecondary)
                    } else {
                        choices.take(20).forEach { insp ->
                            val already = insp.jobId == job.id
                            TextButton(
                                onClick = {
                                    if (!already) onLink(insp.id)
                                    showPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    buildString {
                                        append(insp.customerName.ifBlank { insp.inspectionType.name })
                                        if (insp.speciesIdentified.isNotBlank()) append(" · ").append(insp.speciesIdentified)
                                        if (already) append(" (linked)")
                                        else if (insp.jobId.isNotBlank()) append(" (other job)")
                                    },
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Close", color = TextSecondary) }
            }
        )
    }
}

@Composable
fun InspectionJobLinkCard(
    linkedJobId: String,
    linkedJobTitle: String,
    linkedJobAddress: String,
    jobs: List<Job>,
    suggestedJob: Job?,
    onLink: (String) -> Unit,
    onUnlink: () -> Unit,
    onOpenJob: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val linked = jobs.find { it.id == linkedJobId }
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (linkedJobId.isNotBlank()) "Linked job" else "No job linked",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            if (linkedJobId.isNotBlank()) {
                Text(
                    linkedJobTitle.ifBlank { linked?.title }.orEmpty().ifBlank { "Job" },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (linkedJobAddress.isNotBlank()) {
                    Text(linkedJobAddress, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton(onClick = { onOpenJob(linkedJobId) }) { Text("Open", color = PrimaryGreen) }
                    TextButton(onClick = onUnlink) { Text("Unlink", color = TextSecondary) }
                }
            } else {
                Text(
                    "Pick a job so findings and photos flow into that job’s estimate and report.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                suggestedJob?.let { hint ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Suggested: ${hint.title.ifBlank { hint.customerName }} at ${hint.address.ifBlank { "no address" }} — tap to link.",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    TextButton(onClick = { onLink(hint.id) }) {
                        Text("Link suggested job", color = PrimaryGreen)
                    }
                }
            }
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Link to job")
            }
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            containerColor = BackgroundCard,
            title = { Text("Link to job", color = TextPrimary) },
            text = {
                Column {
                    if (jobs.isEmpty()) {
                        Text("No jobs on this phone yet.", color = TextSecondary)
                    } else {
                        jobs.take(30).forEach { job ->
                            TextButton(
                                onClick = {
                                    onLink(job.id)
                                    showPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    job.title.ifBlank { job.customerName }.ifBlank { job.id } +
                                        job.address.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Close", color = TextSecondary) }
            }
        )
    }
}
