package com.strobingn.wildlifefieldops.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.model.PreferredContact
import com.strobingn.wildlifefieldops.ui.theme.AccentBlue
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobCustomerSection(
    draft: JobCustomerDraft,
    onDraftChange: (JobCustomerDraft) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    matches: List<Customer>,
    onPickCustomer: (Customer) -> Unit,
    onNewCustomer: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var showPreferred by remember { mutableStateOf(false) }
    val showMatches = searchQuery.isNotBlank() && matches.isNotEmpty()

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Customer",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (draft.isLinked()) {
                    Text(
                        "Linked",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryGreen
                    )
                    TextButton(onClick = onNewCustomer) {
                        Text("New customer", color = AccentBlue)
                    }
                }
            }
            Text(
                if (draft.isLinked()) {
                    "Edits here update this customer on every job. Typed values win."
                } else {
                    "Enter a new customer or pick an existing one. Leave blank if unknown."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            ExposedDropdownMenuBox(
                expanded = showMatches,
                onExpandedChange = {}
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Find existing customer") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    placeholder = { Text("Name, phone, email, or address") },
                    colors = jobCustomerFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = showMatches,
                    onDismissRequest = { onSearchQueryChange("") },
                    modifier = Modifier.exposedDropdownSize()
                ) {
                    matches.forEach { customer ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(customer.fullName.ifBlank { "Unnamed" }, color = TextPrimary)
                                    val subtitle = listOf(customer.phone, customer.address)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                    if (subtitle.isNotBlank()) {
                                        Text(subtitle, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            },
                            onClick = { onPickCustomer(customer) },
                            leadingIcon = {
                                Icon(Icons.Default.PersonSearch, contentDescription = null, tint = TextSecondary)
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                label = { Text("Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = draft.companyName,
                onValueChange = { onDraftChange(draft.copy(companyName = it)) },
                label = { Text("Company") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = TextSecondary) },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = draft.phone,
                onValueChange = { onDraftChange(draft.copy(phone = it)) },
                label = { Text("Phone") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = { tapToCall(context, draft.phone) },
                            enabled = draft.phone.isNotBlank()
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = PrimaryGreen)
                        }
                        IconButton(
                            onClick = { tapToText(context, draft.phone) },
                            enabled = draft.phone.isNotBlank()
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "Text", tint = AccentBlue)
                        }
                    }
                },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = draft.email,
                onValueChange = { onDraftChange(draft.copy(email = it)) },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    IconButton(
                        onClick = { tapToEmail(context, draft.email) },
                        enabled = draft.email.isNotBlank()
                    ) {
                        Icon(Icons.Default.Email, contentDescription = "Email", tint = AccentBlue)
                    }
                },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = draft.address,
                onValueChange = { onDraftChange(draft.copy(address = it)) },
                label = { Text("Service address") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    IconButton(
                        onClick = { tapToNavigate(context, draft.composedServiceAddress()) },
                        enabled = draft.composedServiceAddress().isNotBlank()
                    ) {
                        Icon(Icons.Default.Map, contentDescription = "Navigate", tint = PrimaryGreen)
                    }
                },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.city,
                    onValueChange = { onDraftChange(draft.copy(city = it)) },
                    label = { Text("City") },
                    colors = jobCustomerFieldColors(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.state,
                    onValueChange = { onDraftChange(draft.copy(state = it)) },
                    label = { Text("State") },
                    colors = jobCustomerFieldColors(),
                    modifier = Modifier.weight(0.55f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.zipCode,
                    onValueChange = { onDraftChange(draft.copy(zipCode = it)) },
                    label = { Text("ZIP") },
                    colors = jobCustomerFieldColors(),
                    modifier = Modifier.weight(0.7f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = draft.billingAddress,
                onValueChange = { onDraftChange(draft.copy(billingAddress = it)) },
                label = { Text("Billing address (if different)") },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = draft.notes,
                onValueChange = { onDraftChange(draft.copy(notes = it)) },
                label = { Text("Notes / gate codes") },
                colors = jobCustomerFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            ExposedDropdownMenuBox(
                expanded = showPreferred,
                onExpandedChange = { showPreferred = it }
            ) {
                OutlinedTextField(
                    value = draft.preferredContact.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Preferred contact") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPreferred) },
                    colors = jobCustomerFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = showPreferred,
                    onDismissRequest = { showPreferred = false },
                    modifier = Modifier.exposedDropdownSize()
                ) {
                    PreferredContact.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label, color = TextPrimary) },
                            onClick = {
                                onDraftChange(draft.copy(preferredContact = option))
                                showPreferred = false
                            }
                        )
                    }
                }
            }

            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun jobCustomerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = TextSecondary,
    unfocusedLabelColor = TextTertiary,
    focusedContainerColor = BackgroundCard,
    unfocusedContainerColor = BackgroundCard
)

internal fun tapToCall(context: Context, phone: String) {
    val uri = Uri.parse("tel:${phone.trim()}")
    launchExternal(context, Intent(Intent.ACTION_DIAL, uri))
}

internal fun tapToText(context: Context, phone: String) {
    val uri = Uri.parse("smsto:${phone.trim()}")
    launchExternal(context, Intent(Intent.ACTION_SENDTO, uri))
}

internal fun tapToEmail(context: Context, email: String) {
    val uri = Uri.parse("mailto:${email.trim()}")
    launchExternal(context, Intent(Intent.ACTION_SENDTO, uri))
}

internal fun tapToNavigate(context: Context, address: String) {
    val query = Uri.encode(address.trim())
    val maps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$query"))
    if (!launchExternal(context, maps)) {
        launchExternal(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query"))
        )
    }
}

private fun launchExternal(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
