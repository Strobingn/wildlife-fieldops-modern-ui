package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.InventoryItem
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val lowStock by viewModel.lowStockItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showLowStock by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryGreen,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search inventory...", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BackgroundCard,
                    unfocusedContainerColor = BackgroundCard
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Low stock warning
            if (lowStock.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { showLowStock = !showLowStock },
                    colors = CardDefaults.cardColors(containerColor = StatusPending.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusPending)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${lowStock.size} item(s) low on stock", color = StatusPending, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            if (showLowStock) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = StatusPending
                        )
                    }
                }
            }

            if (showLowStock && lowStock.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    lowStock.forEach { item ->
                        LowStockItemCard(item = item, onAdjust = { viewModel.adjustStock(item.id, it) })
                    }
                }
            }

            // Items list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inventory, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No inventory items", color = TextSecondary)
                                TextButton(onClick = { showAddDialog = true }) {
                                    Text("Add item", color = PrimaryGreen)
                                }
                            }
                        }
                    }
                } else {
                    items(items, key = { it.id }) { item ->
                        InventoryItemCard(item = item)
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddInventoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { viewModel.addItem(it) }
        )
    }
}

@Composable
private fun InventoryItemCard(item: InventoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Inventory, contentDescription = null, tint = AccentCyan)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text("SKU: ${item.sku}", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Text("${item.category}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${String.format("%.0f", item.quantityOnHand)} ${item.unitOfMeasure}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isLowStock) StatusPending else TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$${String.format("%.2f", item.unitCost)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun LowStockItemCard(item: InventoryItem, onAdjust: (Double) -> Unit) {
    var showAdjust by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { showAdjust = true },
        colors = CardDefaults.cardColors(containerColor = BackgroundElevated),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text("Stock: ${String.format("%.0f", item.quantityOnHand)} / Reorder: ${String.format("%.0f", item.reorderLevel)}",
                    style = MaterialTheme.typography.labelSmall, color = StatusPending)
            }
            TextButton(onClick = { showAdjust = true }) {
                Text("Adjust", color = PrimaryGreen)
            }
        }
    }

    if (showAdjust) {
        var newQty by remember { mutableStateOf(item.quantityOnHand.toString()) }
        AlertDialog(
            onDismissRequest = { showAdjust = false },
            title = { Text("Adjust Stock: ${item.name}", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newQty,
                    onValueChange = { newQty = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("New Quantity") },
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        newQty.toDoubleOrNull()?.let { onAdjust(it) }
                        showAdjust = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White)
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjust = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BackgroundCard
        )
    }
}

@Composable
private fun AddInventoryDialog(
    onDismiss: () -> Unit,
    onSave: (InventoryItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var unitPrice by remember { mutableStateOf("") }
    var reorderLevel by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Inventory Item", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Qty") }, colors = fieldColors(), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp), singleLine = true)
                    OutlinedTextField(value = unitCost, onValueChange = { unitCost = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Cost") }, colors = fieldColors(), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Price") }, colors = fieldColors(), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp), singleLine = true)
                    OutlinedTextField(value = reorderLevel, onValueChange = { reorderLevel = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Reorder") }, colors = fieldColors(), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp), singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val item = InventoryItem(
                        name = name,
                        sku = sku,
                        category = category,
                        quantityOnHand = qty.toDoubleOrNull() ?: 0.0,
                        unitCost = unitCost.toDoubleOrNull() ?: 0.0,
                        unitPrice = unitPrice.toDoubleOrNull() ?: 0.0,
                        reorderLevel = reorderLevel.toDoubleOrNull() ?: 0.0,
                        location = location
                    )
                    onSave(item)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White),
                enabled = name.isNotBlank()
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = BackgroundCard
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = BackgroundDark,
    unfocusedContainerColor = BackgroundDark
)
