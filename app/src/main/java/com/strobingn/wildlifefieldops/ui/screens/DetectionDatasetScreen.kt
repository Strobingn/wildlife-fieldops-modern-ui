package com.strobingn.wildlifefieldops.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.fieldops.FieldDate
import com.strobingn.wildlifefieldops.data.detection.DetectionFeedbackRepository
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.util.DecLogShare
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DetectionDatasetViewModel @Inject constructor(
    private val repository: DetectionFeedbackRepository,
) : ViewModel() {
    val rows = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _exportPath = MutableStateFlow("")
    val exportPath = _exportPath.asStateFlow()

    fun export(context: Context) {
        viewModelScope.launch {
            val csv = repository.exportCsv()
            val file = File(context.cacheDir, "ai-detection-dataset.csv")
            file.writeText(csv)
            _exportPath.value = file.absolutePath
            runCatching { DecLogShare.shareCsv(context, csv, file.name) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionDatasetScreen(
    onBack: () -> Unit,
    viewModel: DetectionDatasetViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val exportPath by viewModel.exportPath.collectAsState()
    val context = LocalContext.current
    val labeled = rows.count { it.verdict != "UNLABELED" }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detection dataset") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "$labeled labeled · ${rows.size} examples",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "CSV includes the photo path for each example. Dates are MM/dd/yyyy.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Button(
                    onClick = { viewModel.export(context) },
                    enabled = rows.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export CSV") }
            }
            if (exportPath.isNotBlank()) {
                item {
                    Text(
                        "Saved $exportPath",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(rows, key = { it.id }) { row ->
                Column {
                    Text(
                        listOf(row.verdict, row.correctedLabel.ifBlank { row.modelLabel }.ifBlank { "unlabeled" })
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${FieldDate.formatDay(row.createdAt)} · ${row.photoLocalPath.ifBlank { "no photo path" }}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
