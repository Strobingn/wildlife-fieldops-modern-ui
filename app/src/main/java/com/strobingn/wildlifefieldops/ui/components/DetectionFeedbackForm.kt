package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.rememberScrollState as rememberRowScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.detection.DetectionFeedbackPolicy
import com.strobingn.wildlifefieldops.ai.detection.DetectionLabels
import com.strobingn.wildlifefieldops.ai.detection.FeedbackDraft
import com.strobingn.wildlifefieldops.ai.fieldops.FieldDate
import com.strobingn.wildlifefieldops.ai.species.WildlifeSpeciesCatalog
import com.strobingn.wildlifefieldops.ui.theme.ErrorRed
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary

/**
 * Yes / No / Partly correction. Typing works with no signal.
 * The parent saves the draft on this phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetectionFeedbackForm(
    modelLabel: String,
    modelConfidence: Double,
    learnedLabel: String,
    learnedFromCorrections: Boolean,
    takenAtMillis: Long,
    savedMessage: String,
    onSubmit: (FeedbackDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var verdict by rememberSaveable { mutableStateOf("") }
    var what by rememberSaveable { mutableStateOf("") }
    var picker by rememberSaveable { mutableStateOf("") }
    var freeType by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val learnedShown = learnedFromCorrections && learnedLabel.isNotBlank()
    LaunchedEffect(learnedShown, learnedLabel) {
        if (learnedShown && picker.isBlank() && freeType.isBlank()) {
            picker = learnedLabel
        }
    }
    val draft = FeedbackDraft(
        verdict = verdict,
        whatImageShows = what,
        pickerLabel = picker,
        freeTextLabel = freeType,
        notes = notes,
        shownLabel = if (learnedShown) learnedLabel else "",
    )
    val problem = if (verdict.isBlank()) null else DetectionFeedbackPolicy.validate(draft)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Was this right?",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (takenAtMillis > 0L) {
            Text(
                "Photo ${FieldDate.formatDay(takenAtMillis)}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        val said = modelLabel.ifBlank { "No label" }
        val percent = (modelConfidence.coerceIn(0.0, 1.0) * 100).toInt()
        Text(
            "AI said: $said ($percent%)",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (learnedFromCorrections && learnedLabel.isNotBlank()) {
            Text(
                DetectionLabels.LEARNED_BANNER,
                color = PrimaryGreen,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Using: $learnedLabel",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DetectionLabels.VERDICT_YES to "Yes",
                DetectionLabels.VERDICT_NO to "No",
                DetectionLabels.VERDICT_PARTLY to "Partly",
            ).forEach { (value, label) ->
                val selected = verdict == value
                if (selected) {
                    Button(
                        onClick = { verdict = value },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { verdict = value }) { Text(label) }
                }
            }
        }
        OutlinedTextField(
            value = what,
            onValueChange = { what = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Exactly what this image shows") },
            supportingText = {
                Text(
                    if (verdict == DetectionLabels.VERDICT_NO || verdict == DetectionLabels.VERDICT_PARTLY) {
                        "Required for No or Partly"
                    } else {
                        "Optional when the detection was right"
                    }
                )
            },
            minLines = 2,
        )
        Text("Entry point", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetectionLabels.ENTRY_KINDS.forEach { kind ->
                FilterChip(
                    selected = picker == kind,
                    onClick = { picker = if (picker == kind) "" else kind },
                    label = { Text(kind) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryGreen,
                        selectedLabelColor = OnPrimary,
                    ),
                )
            }
        }
        Text("Animal", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberRowScroll()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WildlifeSpeciesCatalog.pickerLabels.forEach { kind ->
                FilterChip(
                    selected = picker == kind,
                    onClick = { picker = if (picker == kind) "" else kind },
                    label = { Text(kind) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryGreen,
                        selectedLabelColor = OnPrimary,
                    ),
                )
            }
        }
        OutlinedTextField(
            value = freeType,
            onValueChange = { freeType = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Or type the type") },
            singleLine = true,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes (optional)") },
            minLines = 2,
        )
        Text(
            "Saves on this phone first. You can type the label with no signal.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!problem.isNullOrBlank()) {
            Text(problem, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
        }
        if (savedMessage.isNotBlank()) {
            Text(savedMessage, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { onSubmit(draft) },
            enabled = problem == null && verdict.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save feedback") }
    }
}

@Composable
fun DetectionFeedbackDialog(
    modelLabel: String,
    modelConfidence: Double,
    learnedLabel: String,
    learnedFromCorrections: Boolean,
    takenAtMillis: Long,
    savedMessage: String,
    onSubmit: (FeedbackDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = { Text("Detection feedback") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DetectionFeedbackForm(
                    modelLabel = modelLabel,
                    modelConfidence = modelConfidence,
                    learnedLabel = learnedLabel,
                    learnedFromCorrections = learnedFromCorrections,
                    takenAtMillis = takenAtMillis,
                    savedMessage = savedMessage,
                    onSubmit = onSubmit,
                )
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        },
    )
}
