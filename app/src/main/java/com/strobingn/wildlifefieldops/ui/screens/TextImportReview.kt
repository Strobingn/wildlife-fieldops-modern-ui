package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.remote.TextMessageImport
import com.strobingn.wildlifefieldops.navigation.ManualJobEntry
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary

internal class ImportGate {
    var title: String = ""
    var description: String = ""
    var notes: String = ""
    var species: String = ""
    var serviceType: String = ""
    var serviceTouched: Boolean = false
    var priority: com.strobingn.wildlifefieldops.data.model.JobPriority =
        com.strobingn.wildlifefieldops.data.model.JobPriority.MEDIUM
    var priorityTouched: Boolean = false
    var customer: JobCustomerDraft = JobCustomerDraft()
    var manual: Set<String> = emptySet()

    fun snapshot(): TextMessageImport.JobSnapshot = TextMessageImport.JobSnapshot(
        title = title,
        description = description,
        notes = notes,
        species = species,
        serviceType = serviceType,
        serviceTouched = serviceTouched,
        priority = priority,
        priorityTouched = priorityTouched,
        customer = customer,
        manual = manual,
        linked = customer.customerId.isNotBlank()
    )
}

internal class CustomerImportGate {
    var firstName: String = ""
    var lastName: String = ""
    var phone: String = ""
    var email: String = ""
    var address: String = ""
    var city: String = ""
    var state: String = ""
    var zip: String = ""
    var notes: String = ""
    var manual: Set<String> = emptySet()

    fun snapshot(): TextMessageImport.CustomerSnapshot = TextMessageImport.CustomerSnapshot(
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        email = email,
        address = address,
        city = city,
        state = state,
        zip = zip,
        notes = notes,
        manual = manual
    )
}

enum class ImportCustomerChoice {
    UNDECIDED,
    USE_EXISTING,
    CREATE_NEW
}

@Composable
fun PasteFromTextButton(
    modifier: Modifier = Modifier,
    onApply: (TextMessageImport.Fields) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = PrimaryGreen)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text("Paste from text", color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
    }
    if (open) {
        PasteFromTextDialog(
            onDismiss = { open = false },
            onApply = { fields ->
                open = false
                onApply(fields)
            }
        )
    }
}

@Composable
private fun PasteFromTextDialog(
    onDismiss: () -> Unit,
    onApply: (TextMessageImport.Fields) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var text by remember {
        mutableStateOf(clipboard.getText()?.text.orEmpty())
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BackgroundCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Paste from text",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Paste a customer text. Empty fields are filled. Typed text stays. Nothing is saved until you tap save.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Customer text") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    colors = jobCustomerFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { text = clipboard.getText()?.text.orEmpty() }) {
                        Text("Read clipboard", color = PrimaryGreen)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = { onApply(TextMessageImport.parse(text)) },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                    ) {
                        Text("Fill empty fields", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun TextImportReviewBanner(quote: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Review this text",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Nothing is saved until you tap Create Job. Edit anything that looks wrong.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            if (quote.isNotBlank()) {
                Text(
                    quote,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ExistingCustomerChoiceCard(
    matches: List<Customer>,
    choice: ImportCustomerChoice,
    onUseExisting: (Customer) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (matches.isEmpty() && choice == ImportCustomerChoice.UNDECIDED) return
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (choice) {
                ImportCustomerChoice.USE_EXISTING -> {
                    Text(
                        "Using the existing customer. Nothing is saved yet.",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onCreateNew) {
                        Text("Create new", color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
                ImportCustomerChoice.CREATE_NEW -> {
                    Text(
                        "Creating a new customer. The other record is left as it is.",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    matches.forEach { customer ->
                        TextButton(onClick = { onUseExisting(customer) }) {
                            Text(
                                "Use existing customer · ${customer.fullName.ifBlank { "Unnamed" }}",
                                color = PrimaryGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                ImportCustomerChoice.UNDECIDED -> {
                    Text(
                        "This phone or address is already on file. Pick one. Nothing is merged unless you choose.",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    matches.forEach { customer ->
                        val detail = listOf(customer.fullName, customer.phone, customer.address)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { onUseExisting(customer) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Use existing customer", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = onCreateNew,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create new", color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * The share-in review Sir sees: customer fields and the job on one page.
 * [onSave] runs only when Create Job is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareInReviewForm(
    fields: TextMessageImport.Fields,
    matches: List<Customer>,
    onBack: () -> Unit,
    onSave: () -> Unit = {},
    saved: Boolean = false
) {
    var choice by remember { mutableStateOf(ImportCustomerChoice.UNDECIDED) }
    var draft by remember {
        mutableStateOf(
            JobCustomerDraft(
                name = fields.name,
                phone = fields.phone,
                email = fields.email,
                address = fields.street,
                city = fields.city,
                state = fields.state,
                zipCode = fields.zip
            )
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ManualJobEntry.ACTION_LABEL, color = TextPrimary) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextImportReviewBanner(fields.sourceText.ifBlank { fields.problem })
            ExistingCustomerChoiceCard(
                matches = matches,
                choice = choice,
                onUseExisting = { customer ->
                    choice = ImportCustomerChoice.USE_EXISTING
                    draft = JobCustomerDraft.fromCustomer(customer)
                },
                onCreateNew = {
                    choice = ImportCustomerChoice.CREATE_NEW
                    draft = JobCustomerDraft(
                        name = fields.name,
                        phone = fields.phone,
                        email = fields.email,
                        address = fields.street,
                        city = fields.city,
                        state = fields.state,
                        zipCode = fields.zip
                    )
                }
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("Name") },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = draft.phone,
                onValueChange = { draft = draft.copy(phone = it) },
                label = { Text("Phone") },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = draft.address,
                onValueChange = { draft = draft.copy(address = it) },
                label = { Text("Address") },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = listOf(draft.city, draft.state, draft.zipCode).filter { it.isNotBlank() }.joinToString(" "),
                onValueChange = {},
                readOnly = true,
                label = { Text("Town, state, ZIP") },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = fields.jobTitle,
                onValueChange = {},
                readOnly = true,
                label = { Text("Job Title *") },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = fields.problem,
                onValueChange = {},
                readOnly = true,
                label = { Text("Problem") },
                colors = jobCustomerFieldColors(),
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = onSave,
                enabled = !saved && fields.jobTitle.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(if (saved) "Saved" else "Create Job", fontWeight = FontWeight.Bold)
            }
        }
    }
}
