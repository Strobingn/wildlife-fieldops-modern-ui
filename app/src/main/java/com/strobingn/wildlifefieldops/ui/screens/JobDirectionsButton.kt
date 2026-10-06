package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.FieldShapes

@Composable
fun JobDirectionsButton(
    target: JobDirections.Target,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val enabled = JobDirections.hasDestination(target)
    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = { JobDirections.open(context, target) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = FieldShapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(Icons.Default.Directions, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Directions", fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (!enabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                JobDirections.MISSING_HINT,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun JobDirectionsIconButton(
    job: Job,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val target = JobDirections.fromJob(job)
    val enabled = JobDirections.hasDestination(target)
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (enabled) JobDirections.open(context, target) }
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = { JobDirections.open(context, target) },
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(
                Icons.Default.Directions,
                contentDescription = if (enabled) "Directions" else JobDirections.MISSING_HINT,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
