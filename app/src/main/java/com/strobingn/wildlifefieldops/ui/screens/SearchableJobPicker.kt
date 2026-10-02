package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.JobSearch
import com.strobingn.wildlifefieldops.ai.fieldops.JobSearchRow
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary

@Composable
fun SearchableJobPicker(
    jobs: List<Job>,
    selectedId: String,
    onSelect: (String) -> Unit,
    allowNone: Boolean = false,
    label: String = "Job",
    modifier: Modifier = Modifier
) {
    var query by remember(selectedId) { mutableStateOf("") }
    val selected = jobs.find { it.id == selectedId }
    val selectedLabel = when {
        selected != null -> jobLabel(selected)
        allowNone && selectedId.isBlank() -> "No job"
        else -> ""
    }
    val rows = jobs.map { job ->
        JobSearchRow(
            id = job.id,
            label = jobLabel(job),
            haystack = listOf(job.title, job.customerName, job.address, job.type).joinToString(" ")
        )
    }
    val shown = JobSearch.filter(rows, query)
    Column(modifier) {
        OutlinedTextField(
            value = JobSearch.fieldValue(query),
            onValueChange = { query = it },
            label = { Text(if (allowNone) "$label (optional)" else label) },
            placeholder = { Text(selectedLabel.ifBlank { "Search jobs" }, color = TextTertiary) },
            supportingText = {
                Text(JobSearch.selectionNote(selectedLabel, query, shown.size), color = TextTertiary)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = pickerColors(),
            singleLine = true
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (allowNone) {
                TextButton(onClick = {
                    onSelect("")
                    query = ""
                }) { Text("No job", color = PrimaryGreen) }
            }
            shown.forEach { row ->
                TextButton(onClick = {
                    onSelect(row.id)
                    query = ""
                }) {
                    Text(
                        if (row.id == selectedId) "● ${row.label}" else row.label,
                        color = if (row.id == selectedId) PrimaryGreen else TextSecondary
                    )
                }
            }
            if (shown.isEmpty()) {
                Text("No jobs match that search.", color = TextTertiary)
            }
        }
    }
}

private fun jobLabel(job: Job): String =
    job.title.ifBlank { job.customerName }.ifBlank { job.address }.ifBlank { job.id }

@Composable
private fun pickerColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
