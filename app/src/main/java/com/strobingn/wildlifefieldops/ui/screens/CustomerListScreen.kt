package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.remote.TextImportEntry
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.CustomersViewModel

data class CustomerListPreview(
    val customers: List<Customer>,
    val customerCount: Int = customers.size,
    val searchQuery: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onNavigateToCustomerForm: (String?) -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true,
    onImportFromText: () -> Unit = {},
    preview: CustomerListPreview? = null
) {
    if (preview != null) {
        CustomerListContent(
            customers = preview.customers,
            customerCount = preview.customerCount,
            searchQuery = preview.searchQuery,
            onSearch = {},
            onNavigateToCustomerForm = onNavigateToCustomerForm,
            onImportFromText = onImportFromText,
            onBack = onBack,
            showBack = showBack
        )
        return
    }
    val viewModel: CustomersViewModel = hiltViewModel()
    val customers by viewModel.customers.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val customerCount by viewModel.customerCount.collectAsState()
    CustomerListContent(
        customers = customers,
        customerCount = customerCount,
        searchQuery = searchQuery,
        onSearch = viewModel::setSearchQuery,
        onNavigateToCustomerForm = onNavigateToCustomerForm,
        onImportFromText = onImportFromText,
        onBack = onBack,
        showBack = showBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerListContent(
    customers: List<Customer>,
    customerCount: Int,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onNavigateToCustomerForm: (String?) -> Unit,
    onImportFromText: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean
) {
    Scaffold(
        topBar = {
            FieldTopBar(
                title = "Customers",
                onBack = if (showBack) onBack else null
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            ExtendedFloatingActionButton(
                onClick = onImportFromText,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = FieldShapes.fab,
                icon = { Icon(Icons.Default.Sms, contentDescription = null) },
                text = { Text(TextImportEntry.ACTION_LABEL, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            )
            ExtendedFloatingActionButton(
                onClick = { onNavigateToCustomerForm(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = FieldShapes.fab,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Add", fontWeight = FontWeight.SemiBold) }
            )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                CountSummaryCell(
                    label = "Customers",
                    count = customerCount,
                    modifier = Modifier.weight(1f)
                )
            }

            FieldSearchBar(
                value = searchQuery,
                onValueChange = onSearch,
                placeholder = "Search customers…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Text(
                "${customers.size} customer${if (customers.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (customers.isEmpty()) {
                    item {
                        EmptyState(
                            icon = {
                                Icon(
                                    Icons.Default.PeopleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = "No customers yet",
                            subtitle = "Add a customer to start logging jobs",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(customers, key = { it.id }) { customer ->
                        CustomerListItem(
                            customer = customer,
                            onClick = { onNavigateToCustomerForm(customer.id) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun CustomerListItem(customer: Customer, onClick: () -> Unit) {
    FieldCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentPurple.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${customer.firstName.firstOrNull() ?: ""}${customer.lastName.firstOrNull() ?: ""}",
                    color = AccentPurple,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    customer.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (customer.phone.isNotBlank()) {
                    Text(
                        customer.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (customer.address.isNotBlank()) {
                    Text(
                        "${customer.address}, ${customer.city}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
