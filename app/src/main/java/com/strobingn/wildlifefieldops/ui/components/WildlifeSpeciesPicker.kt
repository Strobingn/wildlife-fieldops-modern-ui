package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.species.SpeciesSuggestion
import com.strobingn.wildlifefieldops.ai.species.WildlifeSpeciesCatalog
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary

@Composable
fun SpeciesIdentificationBlock(
    suggestion: SpeciesSuggestion?,
    selectedLabel: String,
    onPick: (String) -> Unit,
    analyzing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Species ID", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
        if (analyzing) {
            Text(
                "Identifying species…",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (suggestion != null) {
            val unsure = !suggestion.confident || suggestion.primaryLabel == WildlifeSpeciesCatalog.UNSURE
            Text(
                if (unsure) {
                    "${WildlifeSpeciesCatalog.UNSURE} (${suggestion.backendTag})"
                } else {
                    "Suggested: ${suggestion.primaryLabel} · ${suggestion.confidencePercent}% (${suggestion.backendTag})"
                },
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            val alternates = suggestion.alternatives
                .map { it.label }
                .filter { !it.equals(suggestion.primaryLabel, ignoreCase = true) }
                .filter { it != WildlifeSpeciesCatalog.UNSURE }
                .distinct()
            if (alternates.isNotEmpty()) {
                Text("Also considered", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                SpeciesChipRow(labels = alternates, selected = selectedLabel, onPick = onPick)
            }
            if (suggestion.evidenceNotes.isNotBlank()) {
                Text(suggestion.evidenceNotes, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            suggestion.safety.notes.take(2).forEach { note ->
                Text("• $note", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        } else if (!analyzing) {
            Text(
                "No species suggestion. Pick one below or type it. Nothing counts until you confirm.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("Tap to set species", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        SpeciesChipRow(
            labels = WildlifeSpeciesCatalog.pickerLabels,
            selected = selectedLabel,
            onPick = onPick,
        )
    }
}

@Composable
private fun SpeciesChipRow(
    labels: List<String>,
    selected: String,
    onPick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { label ->
            FilterChip(
                selected = selected.equals(label, ignoreCase = true),
                onClick = { onPick(label) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryGreen,
                    selectedLabelColor = OnPrimary,
                ),
            )
        }
    }
}
