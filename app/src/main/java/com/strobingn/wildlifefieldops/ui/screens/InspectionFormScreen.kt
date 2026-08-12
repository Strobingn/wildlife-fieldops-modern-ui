package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.components.ScheduleDateTimeField
import com.strobingn.wildlifefieldops.ui.components.defaultAppointmentTime
import com.strobingn.wildlifefieldops.ui.viewmodel.InspectionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionFormScreen(
    inspectionId: String? = null,
    prefilledJobId: String = "",
    onBack: () -> Unit,
    viewModel: InspectionsViewModel = hiltViewModel()
) {
    var customerName by remember { mutableStateOf("") }
    var inspectorName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(InspectionType.ROUTINE) }
    var findings by remember { mutableStateOf("") }
    var recommendations by remember { mutableStateOf("") }
    var selectedSeverity by remember { mutableStateOf(FindingSeverity.NONE) }
    var speciesIdentified by remember { mutableStateOf("") }
    var entryPoints by remember { mutableStateOf("") }
    var damageAssessment by remember { mutableStateOf("") }
    var followUpRequired by remember { mutableStateOf(false) }
    var weatherConditions by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showSeverityDropdown by remember { mutableStateOf(false) }
    var scheduledAt by remember { mutableStateOf(defaultAppointmentTime()) }
    val existing by viewModel.getInspectionById(inspectionId.orEmpty())
        .collectAsState(initial = null)

    LaunchedEffect(existing) {
        val insp = existing ?: return@LaunchedEffect
        customerName = insp.customerName
        inspectorName = insp.inspectorName
        selectedType = insp.inspectionType
        findings = insp.findings
        recommendations = insp.recommendations
        selectedSeverity = insp.severity
        speciesIdentified = insp.speciesIdentified
        entryPoints = insp.entryPoints
        damageAssessment = insp.damageAssessment
        followUpRequired = insp.followUpRequired
        weatherConditions = insp.weatherConditions
        notes = insp.notes
        scheduledAt = insp.inspectionDate
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (inspectionId.isNullOrBlank()) "New Inspection" else "Inspection",
                        color = TextPrimary
                    )
                },
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
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("Customer Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = inspectorName,
                onValueChange = { inspectorName = it },
                label = { Text("Inspector Name") },
                leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Text(
                "Inspection date and time",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            ScheduleDateTimeField(
                value = scheduledAt,
                onValueChange = { scheduledAt = it }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = showTypeDropdown,
                    onExpandedChange = { showTypeDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedType.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown) },
                        colors = fieldColors(),
                        modifier = Modifier.menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false },
                        modifier = Modifier.exposedDropdownSize()
                    ) {
                        InspectionType.entries.forEach { type ->
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

                ExposedDropdownMenuBox(
                    expanded = showSeverityDropdown,
                    onExpandedChange = { showSeverityDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedSeverity.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Severity") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSeverityDropdown) },
                        colors = fieldColors(),
                        modifier = Modifier.menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showSeverityDropdown,
                        onDismissRequest = { showSeverityDropdown = false },
                        modifier = Modifier.exposedDropdownSize()
                    ) {
                        FindingSeverity.entries.forEach { severity ->
                            DropdownMenuItem(
                                text = { Text(severity.name.lowercase().replaceFirstChar { it.uppercase() }, color = TextPrimary) },
                                onClick = {
                                    selectedSeverity = severity
                                    showSeverityDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = speciesIdentified,
                onValueChange = { speciesIdentified = it },
                label = { Text("Species Identified") },
                leadingIcon = { Icon(Icons.Default.Pets, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = findings,
                onValueChange = { findings = it },
                label = { Text("Findings") },
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )

            OutlinedTextField(
                value = recommendations,
                onValueChange = { recommendations = it },
                label = { Text("Recommendations") },
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            OutlinedTextField(
                value = entryPoints,
                onValueChange = { entryPoints = it },
                label = { Text("Entry Points Found") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = damageAssessment,
                onValueChange = { damageAssessment = it },
                label = { Text("Damage Assessment") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = weatherConditions,
                onValueChange = { weatherConditions = it },
                label = { Text("Weather Conditions") },
                leadingIcon = { Icon(Icons.Default.WbCloudy, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Follow-up Required", color = TextPrimary)
                Switch(
                    checked = followUpRequired,
                    onCheckedChange = { followUpRequired = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen, checkedTrackColor = PrimaryGreen.copy(alpha = 0.5f))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val base = existing
                    if (base != null) {
                        viewModel.updateInspection(
                            base.copy(
                                customerName = customerName,
                                inspectorName = inspectorName,
                                inspectionType = selectedType,
                                inspectionDate = scheduledAt,
                                findings = findings,
                                recommendations = recommendations,
                                severity = selectedSeverity,
                                speciesIdentified = speciesIdentified,
                                entryPoints = entryPoints,
                                damageAssessment = damageAssessment,
                                followUpRequired = followUpRequired,
                                followUpDate = if (followUpRequired) {
                                    base.followUpDate ?: (System.currentTimeMillis() + 7 * 86400000L)
                                } else null,
                                weatherConditions = weatherConditions,
                                notes = notes,
                                isSynced = false
                            )
                        )
                    } else {
                        viewModel.createInspection(
                            jobId = prefilledJobId,
                            customerId = "",
                            customerName = customerName,
                            inspectorName = inspectorName,
                            inspectionType = selectedType,
                            inspectionDate = scheduledAt,
                            findings = findings,
                            recommendations = recommendations,
                            severity = selectedSeverity,
                            speciesIdentified = speciesIdentified,
                            entryPoints = entryPoints,
                            damageAssessment = damageAssessment,
                            followUpRequired = followUpRequired,
                            followUpDate = if (followUpRequired) System.currentTimeMillis() + 7 * 86400000L else null,
                            weatherConditions = weatherConditions,
                            notes = notes
                        )
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                enabled = customerName.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Inspection", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
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
