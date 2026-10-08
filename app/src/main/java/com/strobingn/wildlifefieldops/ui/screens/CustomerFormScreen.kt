package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.CustomerType
import com.strobingn.wildlifefieldops.data.remote.TextMessageImport
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.CustomersViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.TextImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerFormScreen(
    customerId: String? = null,
    onBack: () -> Unit,
    viewModel: CustomersViewModel = hiltViewModel(),
    textImportViewModel: TextImportViewModel = hiltViewModel()
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(CustomerType.RESIDENTIAL) }
    var notes by remember { mutableStateOf("") }
    var billingAddress by remember { mutableStateOf("") }
    var billingContact by remember { mutableStateOf("") }
    var paymentTerms by remember { mutableStateOf("Net 30") }
    var showTypeDropdown by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var activeCustomerId by remember(customerId) { mutableStateOf(customerId) }
    // The row as loaded, so saving keeps fields this form does not show (alternate phone, coordinates, active flag, created date).
    var loadedCustomer by remember(customerId) { mutableStateOf<Customer?>(null) }
    var isSavingCustomer by remember { mutableStateOf(false) }
    var manualKeys by remember { mutableStateOf(setOf<String>()) }
    var importMatches by remember { mutableStateOf(listOf<Customer>()) }
    var importChoice by remember { mutableStateOf(ImportCustomerChoice.UNDECIDED) }
    var lastParsed by remember { mutableStateOf(TextMessageImport.Fields()) }
    val customerGate = remember { CustomerImportGate() }

    SideEffect {
        customerGate.firstName = firstName
        customerGate.lastName = lastName
        customerGate.phone = phone
        customerGate.email = email
        customerGate.address = address
        customerGate.city = city
        customerGate.state = state
        customerGate.zip = zipCode
        customerGate.notes = notes
        customerGate.manual = manualKeys
    }

    fun applyCustomerImport(fields: TextMessageImport.Fields) {
        lastParsed = TextMessageImport.fillEmpty(lastParsed, fields)
        if (importChoice == ImportCustomerChoice.USE_EXISTING) return
        val next = TextMessageImport.applyToCustomer(customerGate.snapshot(), fields)
        firstName = next.firstName
        lastName = next.lastName
        phone = next.phone
        email = next.email
        address = next.address
        city = next.city
        state = next.state
        zipCode = next.zip
        notes = next.notes
    }

    suspend fun refreshImportMatches() {
        if (importChoice != ImportCustomerChoice.UNDECIDED) return
        importMatches = textImportViewModel.matches(lastParsed)
    }

    val importScope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PrimaryGreen,
        unfocusedBorderColor = BorderDark,
        focusedLabelColor = PrimaryGreen,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedContainerColor = BackgroundCard,
        unfocusedContainerColor = BackgroundCard
    )

    LaunchedEffect(customerId) {
        customerId?.let { id ->
            viewModel.getCustomerById(id).collect { customer ->
                customer?.let {
                    loadedCustomer = it
                    isEditing = true
                    firstName = it.firstName
                    lastName = it.lastName
                    companyName = it.companyName
                    email = it.email
                    phone = it.phone
                    address = it.address
                    city = it.city
                    state = it.state
                    zipCode = it.zipCode
                    selectedType = it.customerType
                    notes = it.notes
                    billingAddress = it.billingAddress
                    billingContact = it.billingContact
                    paymentTerms = it.paymentTerms
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Customer" else "New Customer", color = TextPrimary) },
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
            PasteFromTextButton(onApply = { fields ->
                importScope.launch {
                    applyCustomerImport(fields)
                    refreshImportMatches()
                    val source = fields.sourceText
                    if (source.isNotBlank()) {
                        applyCustomerImport(textImportViewModel.refine(source, null))
                        refreshImportMatches()
                    }
                }
            })
            if (importMatches.isNotEmpty() || importChoice != ImportCustomerChoice.UNDECIDED) {
                ExistingCustomerChoiceCard(
                    matches = importMatches,
                    choice = importChoice,
                    onUseExisting = { customer ->
                        importChoice = ImportCustomerChoice.USE_EXISTING
                        activeCustomerId = customer.id
                        isEditing = true
                        firstName = customer.firstName
                        lastName = customer.lastName
                        companyName = customer.companyName
                        email = customer.email
                        phone = customer.phone
                        address = customer.address
                        city = customer.city
                        state = customer.state
                        zipCode = customer.zipCode
                        notes = customer.notes
                        billingAddress = customer.billingAddress
                        billingContact = customer.billingContact
                        paymentTerms = customer.paymentTerms
                        selectedType = customer.customerType
                    },
                    onCreateNew = {
                        importChoice = ImportCustomerChoice.CREATE_NEW
                        activeCustomerId = null
                        isEditing = false
                        val restored = TextMessageImport.applyToCustomer(
                            customerGate.snapshot().copy(
                                firstName = "",
                                lastName = "",
                                phone = "",
                                email = "",
                                address = "",
                                city = "",
                                state = "",
                                zip = "",
                                notes = ""
                            ),
                            lastParsed
                        )
                        firstName = restored.firstName
                        lastName = restored.lastName
                        phone = restored.phone
                        email = restored.email
                        address = restored.address
                        city = restored.city
                        state = restored.state
                        zipCode = restored.zip
                        notes = restored.notes
                    }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = {
                        firstName = it
                        manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.FIRST)
                        manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.LAST)
                    },
                    label = { Text("First Name *") },
                    colors = fieldColors,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = {
                        lastName = it
                        manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.FIRST)
                        manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.LAST)
                    },
                    label = { Text("Last Name *") },
                    colors = fieldColors,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = companyName,
                onValueChange = { companyName = it },
                label = { Text("Company Name") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.PHONE)
                },
                label = { Text("Phone") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.EMAIL)
                },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.STREET)
                },
                label = { Text("Address") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = {
                        city = it
                        manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.CITY)
                    },
                    label = { Text("City") },
                    colors = fieldColors,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state,
                    onValueChange = {
                        state = it
                        manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.STATE)
                    },
                    label = { Text("State") },
                    colors = fieldColors,
                    modifier = Modifier.weight(0.6f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = zipCode,
                    onValueChange = {
                        zipCode = it
                        manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.ZIP)
                    },
                    label = { Text("ZIP") },
                    colors = fieldColors,
                    modifier = Modifier.weight(0.7f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            ExposedDropdownMenuBox(
                expanded = showTypeDropdown,
                onExpandedChange = { showTypeDropdown = it }
            ) {
                OutlinedTextField(
                    value = selectedType.name.lowercase().replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Customer Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown) },
                    colors = fieldColors,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = showTypeDropdown,
                    onDismissRequest = { showTypeDropdown = false },
                    modifier = Modifier.exposedDropdownSize()
                ) {
                    CustomerType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, color = TextPrimary) },
                            onClick = {
                                selectedType = type
                                showTypeDropdown = false
                            }
                        )
                    }
                }
            }

            // Billing section
            Text("Billing Information", style = MaterialTheme.typography.titleSmall, color = TextPrimary)

            OutlinedTextField(
                value = billingAddress,
                onValueChange = { billingAddress = it },
                label = { Text("Billing Address") },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = paymentTerms,
                onValueChange = { paymentTerms = it },
                label = { Text("Payment Terms") },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = {
                    notes = it
                    manualKeys = TextMessageImport.markEdited(manualKeys, TextMessageImport.CUSTOMER_NOTES)
                },
                label = { Text("Notes") },
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isSavingCustomer) return@Button
                    isSavingCustomer = true
                    val base = loadedCustomer
                    if (isEditing && activeCustomerId != null) {
                        val addressChanged = base != null && (
                            base.address != address || base.city != city ||
                                base.state != state || base.zipCode != zipCode
                            )
                        viewModel.updateCustomer(
                            (base ?: Customer(id = activeCustomerId!!)).copy(
                                firstName = firstName,
                                lastName = lastName,
                                companyName = companyName,
                                email = email,
                                phone = phone,
                                address = address,
                                city = city,
                                state = state,
                                zipCode = zipCode,
                                latitude = if (addressChanged) null else base?.latitude,
                                longitude = if (addressChanged) null else base?.longitude,
                                customerType = selectedType,
                                notes = notes,
                                billingAddress = billingAddress,
                                billingContact = billingContact,
                                paymentTerms = paymentTerms
                            ),
                            onDone = onBack
                        )
                    } else {
                        viewModel.createCustomer(
                            firstName = firstName,
                            lastName = lastName,
                            companyName = companyName,
                            email = email,
                            phone = phone,
                            address = address,
                            city = city,
                            state = state,
                            zipCode = zipCode,
                            customerType = selectedType,
                            notes = notes,
                            billingAddress = billingAddress,
                            billingContact = billingContact,
                            paymentTerms = paymentTerms,
                            onDone = onBack
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                shape = RoundedCornerShape(12.dp),
                enabled = firstName.isNotBlank() && lastName.isNotBlank() && !isSavingCustomer
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEditing) "Update Customer" else "Create Customer", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
