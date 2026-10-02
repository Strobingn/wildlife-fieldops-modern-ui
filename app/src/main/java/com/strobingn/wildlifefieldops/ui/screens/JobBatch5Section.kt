package com.strobingn.wildlifefieldops.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.ShareableReport
import com.strobingn.wildlifefieldops.ai.fieldops.SpeciesChecklist
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.SearchFieldOpsViewModel
import com.strobingn.wildlifefieldops.util.QrBitmap
import com.strobingn.wildlifefieldops.util.WildlifeWhispererInspectionReportPdf
import java.io.File

@Composable
fun JobBatch5Section(
    job: Job,
    searchVm: SearchFieldOpsViewModel
) {
    val photos by searchVm.photos.collectAsState()
    val jobPhotos = photos.filter { it.jobId == job.id }
    val message by searchVm.message.collectAsState()
    val context = LocalContext.current
    val qr: Bitmap = remember(job.id) { QrBitmap.encode(ShareableReport.payload(job.id), 256) }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Species checklist", color = TextPrimary, fontWeight = FontWeight.Medium)
            val items = job.pricing.speciesChecklist
            if (items.isEmpty()) {
                Text("Load a raccoon, bat, squirrel, skunk, or rodent list. Check off as you go.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { searchVm.applyChecklist(job) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Load checklist") }
            } else {
                val (done, total) = SpeciesChecklist.completion(items)
                Text("$done / $total  ·  ${items.firstOrNull()?.species.orEmpty()}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                items.forEach { item ->
                    var notes by remember(item.id, item.notes) { mutableStateOf(item.notes) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.done,
                            onCheckedChange = { checked ->
                                searchVm.saveChecklist(job.id, items.map { if (it.id == item.id) it.copy(done = checked) else it })
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(item.label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notes") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = batch5FieldColors()
                            )
                            if (notes != item.notes) {
                                OutlinedButton(onClick = {
                                    searchVm.saveChecklist(job.id, items.map { if (it.id == item.id) it.copy(notes = notes) else it })
                                }) { Text("Save note") }
                            }
                        }
                    }
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Before / after", color = TextPrimary, fontWeight = FontWeight.Medium)
            if (job.pricing.photoPairs.isEmpty()) {
                Text("Pair photos from the gallery. ${jobPhotos.size} photos on this job.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            }
            job.pricing.photoPairs.forEach { pair ->
                val before = jobPhotos.firstOrNull { it.id == pair.beforeId }?.description ?: pair.beforeId.take(8)
                val after = jobPhotos.firstOrNull { it.id == pair.afterId }?.description ?: pair.afterId.take(8)
                Text("Before: $before  →  After: $after", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (pair.notes.isNotBlank()) Text(pair.notes, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Shareable report + QR", color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("Branded PDF plus a QR that opens this job report on this phone.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            Image(bitmap = qr.asImageBitmap(), contentDescription = "Report QR", modifier = Modifier.size(160.dp))
            Text(ShareableReport.payload(job.id), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val path = WildlifeWhispererInspectionReportPdf.generate(
                            context = context,
                            fields = WildlifeWhispererInspectionReportPdf.ReportFields(
                                customerName = job.customerName,
                                jobTitle = job.title,
                                jobAddress = job.address,
                                species = job.confirmedSpecies.ifBlank { job.type },
                                findings = job.notes,
                                notes = job.legalNotes,
                                recommendations = job.nextStep
                            ),
                            qr = qr
                        )
                        searchVm.saveShareReport(job.id, path)
                        WildlifeWhispererInspectionReportPdf.share(context, path)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Share PDF + QR") }
                if (job.pricing.shareReportPath.isNotBlank() && File(job.pricing.shareReportPath).exists()) {
                    OutlinedButton(onClick = {
                        WildlifeWhispererInspectionReportPdf.view(context, job.pricing.shareReportPath)
                    }) { Text("Open saved") }
                }
            }
            message?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun batch5FieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
