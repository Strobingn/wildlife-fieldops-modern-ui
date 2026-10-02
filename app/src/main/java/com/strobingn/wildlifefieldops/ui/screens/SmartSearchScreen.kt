package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.SearchHit
import com.strobingn.wildlifefieldops.ai.fieldops.SearchKind
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.SearchFieldOpsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSearchScreen(
    onBack: () -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenInspection: (String) -> Unit = {},
    viewModel: SearchFieldOpsViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val hits by viewModel.hits.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.search(it) },
                label = { Text("Jobs, customers, notes, tags, findings") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (query.length >= 2 && hits.isEmpty()) {
                    item { Text("No matches.", color = TextSecondary) }
                }
                items(hits, key = { it.kind.name + it.id }) { hit ->
                    SearchHitCard(hit) {
                        when (hit.kind) {
                            SearchKind.JOB, SearchKind.PHOTO, SearchKind.CUSTOMER ->
                                if (hit.jobId.isNotBlank()) onOpenJob(hit.jobId)
                                else if (hit.kind == SearchKind.JOB) onOpenJob(hit.id)
                            SearchKind.INSPECTION -> onOpenInspection(hit.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitCard(hit: SearchHit, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hit.kind.name.lowercase().replaceFirstChar { it.uppercase() }, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(hit.title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            if (hit.snippet.isNotBlank()) {
                Text(hit.snippet, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}
