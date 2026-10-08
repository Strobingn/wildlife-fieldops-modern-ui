package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.strobingn.wildlifefieldops.ai.fieldops.ScheduledInspections
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.ui.theme.AccentBlue
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary

/**
 * Shown at the top of a job that is still only a scheduled inspection.
 * Approve keeps the same row (report, photos, notes) and makes it a real job.
 */
@Composable
fun ScheduledInspectionCard(
    job: Job,
    onSetStatus: (JobStatus) -> Unit
) {
    if (!ScheduledInspections.canApprove(job)) return
    var confirmDecline by remember { mutableStateOf(false) }
    val appointment = ScheduledInspections.appointmentText(job.scheduledDate)

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scheduled inspection", color = AccentBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                if (appointment.isBlank()) "No appointment time set yet. Edit to add one so it shows on the Schedule."
                else "Inspection $appointment. Not a job yet — the customer has only agreed to an inspection.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Text a reminder from Customer message below. When they approve the work, make it a job.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onSetStatus(ScheduledInspections.APPROVED_STATUS) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    modifier = Modifier.weight(1f)
                ) { Text("Approved — make it a job") }
                OutlinedButton(
                    onClick = { confirmDecline = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Declined") }
            }
        }
    }

    if (confirmDecline) {
        AlertDialog(
            onDismissRequest = { confirmDecline = false },
            title = { Text("Customer declined?", color = TextPrimary) },
            text = { Text("This marks the inspection cancelled. You can change the status back later.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onSetStatus(ScheduledInspections.DECLINED_STATUS)
                    confirmDecline = false
                }) { Text("Mark declined", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDecline = false }) { Text("Keep", color = TextSecondary) }
            }
        )
    }
}
