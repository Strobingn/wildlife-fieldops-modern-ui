package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.DuplicateMatch
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.CustomerFieldOpsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateCustomerScreen(
    onBack: () -> Unit,
    viewModel: CustomerFieldOpsViewModel = hiltViewModel()
) {
    val matches by viewModel.duplicates.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val notice by viewModel.message.collectAsState()
    val undo by viewModel.mergeUndo.collectAsState()
    var query by remember { mutableStateOf("") }
    var keepId by remember { mutableStateOf("") }
    var dropId by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    val active = customers.filter { it.isActive }
    val filtered = active.filter { customer ->
        query.isBlank() || listOf(customer.fullName, customer.phone, customer.address, customer.city)
            .joinToString(" ")
            .contains(query, ignoreCase = true)
    }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate customers", color = TextPrimary) },
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
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            notice?.let { item { Text(it, color = TextSecondary) } }
            if (undo != null) {
                item {
                    OutlinedButton(onClick = { viewModel.undoMerge() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Undo last merge")
                    }
                }
            }
            item {
                Text("Merge any two", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Detected pairs are suggestions. Pick any two active customers. Nothing merges until you confirm.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search customers") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Keep: ${active.find { it.id == keepId }?.fullName?.ifBlank { "selected" } ?: "none"}",
                    color = TextSecondary
                )
                Text(
                    "Remove: ${active.find { it.id == dropId }?.fullName?.ifBlank { "selected" } ?: "none"}",
                    color = TextSecondary
                )
            }
            items(filtered.take(30), key = { "pick-" + it.id }) { customer ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(customer.fullName.ifBlank { "Unnamed" }, color = TextPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { keepId = customer.id }) { Text("Keep") }
                    TextButton(onClick = { dropId = customer.id }) { Text("Remove") }
                }
            }
            item {
                Button(
                    onClick = {
                        if (keepId.isNotBlank() && dropId.isNotBlank() && keepId != dropId) {
                            confirm = keepId to dropId
                        }
                    },
                    enabled = keepId.isNotBlank() && dropId.isNotBlank() && keepId != dropId,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Review merge") }
            }
            item {
                Text("Detected pairs", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            if (matches.isEmpty()) {
                item { Text("No same phone, address, or name pairs.", color = TextSecondary) }
            }
            items(matches, key = { it.left.id + it.right.id }) { match ->
                DuplicateCard(
                    match = match,
                    onKeepLeft = { confirm = match.left.id to match.right.id },
                    onKeepRight = { confirm = match.right.id to match.left.id }
                )
            }
        }
    }
    confirm?.let { (keep, drop) ->
        val keepCustomer = customers.find { it.id == keep }
        val dropCustomer = customers.find { it.id == drop }
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = BackgroundCard,
            title = { Text("Merge these customers?", color = TextPrimary) },
            text = {
                Text(
                    "Keep ${keepCustomer?.fullName?.ifBlank { "the first" } ?: "the first"}. " +
                        "${dropCustomer?.fullName?.ifBlank { "The other" } ?: "The other"} is marked inactive and its jobs move. You can undo this merge from this screen.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.mergeCustomers(keep, drop)
                    confirm = null
                }) { Text("Merge", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun DuplicateCard(
    match: DuplicateMatch,
    onKeepLeft: () -> Unit,
    onKeepRight: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                match.reasons.joinToString(" · "),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            CustomerLine(match.left)
            CustomerLine(match.right)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onKeepLeft,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Keep ${shortName(match.left)}") }
                OutlinedButton(onClick = onKeepRight) { Text("Keep ${shortName(match.right)}") }
            }
        }
    }
}

@Composable
private fun CustomerLine(customer: Customer) {
    Column {
        Text(customer.fullName.ifBlank { "Unnamed" }, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        val bits = listOf(customer.phone, customer.address, customer.city).filter { it.isNotBlank() }
        if (bits.isNotEmpty()) {
            Text(bits.joinToString(" · "), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun shortName(customer: Customer): String =
    customer.personName.ifBlank { customer.companyName }.ifBlank { "this one" }.split(" ").first()
