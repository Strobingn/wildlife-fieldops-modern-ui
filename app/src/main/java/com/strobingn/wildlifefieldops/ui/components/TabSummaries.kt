package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.navigation.ManualJobEntry
import com.strobingn.wildlifefieldops.navigation.VoiceJobEntry
import com.strobingn.wildlifefieldops.ui.theme.AccentBlue
import com.strobingn.wildlifefieldops.ui.theme.AccentCyan
import com.strobingn.wildlifefieldops.ui.theme.AccentPurple
import com.strobingn.wildlifefieldops.ui.theme.FieldMetrics
import com.strobingn.wildlifefieldops.ui.theme.FieldShapes
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen

/**
 * Compact count moved off Home. Optional tap toggles a filter on the screen that owns it.
 */
@Composable
fun CountSummaryCell(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        modifier = modifier
            .heightIn(min = FieldMetrics.minTouch)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
        shape = FieldShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun JobFlagSummaryRow(
    scheduled: Int,
    inProgress: Int,
    completed: Int,
    selected: JobStatus?,
    onSelect: (JobStatus?) -> Unit,
    openOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CountSummaryCell(
            label = "Scheduled",
            count = scheduled,
            selected = openOnly || selected == JobStatus.SCHEDULED,
            onClick = {
                onSelect(if (selected == JobStatus.SCHEDULED) null else JobStatus.SCHEDULED)
            },
            modifier = Modifier.weight(1f)
        )
        CountSummaryCell(
            label = "In progress",
            count = inProgress,
            selected = openOnly || selected == JobStatus.IN_PROGRESS,
            onClick = {
                onSelect(if (selected == JobStatus.IN_PROGRESS) null else JobStatus.IN_PROGRESS)
            },
            modifier = Modifier.weight(1f)
        )
        CountSummaryCell(
            label = "Completed",
            count = completed,
            selected = selected == JobStatus.COMPLETED,
            onClick = {
                onSelect(if (selected == JobStatus.COMPLETED) null else JobStatus.COMPLETED)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

/** Home quick-action grid, now the top group on More. */
@Composable
fun QuickActionsGrid(
    onNewJob: () -> Unit,
    onDictate: () -> Unit,
    onSchedule: () -> Unit,
    onMap: () -> Unit,
    onInspect: () -> Unit,
    onRoutes: () -> Unit,
    onReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FieldMetrics.space12)) {
        SectionHeader(title = "Quick actions")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
        ) {
            QuickActionTile(ManualJobEntry.ACTION_LABEL, Icons.Default.AddBox, PrimaryGreen, Modifier.weight(1f), onNewJob)
            QuickActionTile(VoiceJobEntry.ACTION_LABEL, Icons.Default.Mic, PrimaryGreen, Modifier.weight(1f), onDictate)
            QuickActionTile("Schedule", Icons.Default.CalendarMonth, AccentPurple, Modifier.weight(1f), onSchedule)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
        ) {
            QuickActionTile("Map", Icons.Default.Map, AccentBlue, Modifier.weight(1f), onMap)
            QuickActionTile("Inspect", Icons.Default.Search, AccentCyan, Modifier.weight(1f), onInspect)
            QuickActionTile("Routes", Icons.Default.Route, AccentBlue, Modifier.weight(1f), onRoutes)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
        ) {
            QuickActionTile("Reports", Icons.Default.Assessment, AccentBlue, Modifier.weight(1f), onReports)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
