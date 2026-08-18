package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.strobingn.wildlifefieldops.ai.HybridAIService
import com.strobingn.wildlifefieldops.ai.PhotoAIHelper
import com.strobingn.wildlifefieldops.data.model.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.components.ScheduleDateTimeField
import com.strobingn.wildlifefieldops.ui.components.defaultAppointmentTime
import com.strobingn.wildlifefieldops.ui.viewmodel.InspectionsViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionFormScreen(
    inspectionId: String? = null,
    prefilledJobId: String = "",
    onBack: () -> Unit,
    viewModel: InspectionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    var voiceFieldNotes by remember { mutableStateOf("") }
    var photoUris by remember { mutableStateOf(listOf<String>()) }
    var aiReportSource by remember { mutableStateOf("") }
    var isDrafting by remember { mutableStateOf(false) }
    var draftMessage by remember { mutableStateOf<String?>(null) }
    var dictationUnavailable by remember { mutableStateOf(false) }

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
        voiceFieldNotes = insp.voiceFieldNotes
        photoUris = insp.photos
        aiReportSource = insp.aiReportSource
    }

    // Voice dictation via the system speech recognizer - no in-app mic permission needed,
    // the invoked recognizer activity owns the microphone.
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrBlank()) {
                voiceFieldNotes = if (voiceFieldNotes.isBlank()) text else "$voiceFieldNotes $text"
            }
        }
    }

    // Camera capture for inspection photos.
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri?.let { uri -> photoUris = photoUris + uri.toString() }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createInspectionPhotoUri(context)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe what you're seeing on this inspection")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { dictationUnavailable = true }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createInspectionPhotoUri(context)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
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

            HorizontalDivider(color = BorderDark)

            Text(
                "Talk through the inspection",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Dictate what you're seeing, snap photos of evidence, then let AI draft the report fields below for your review.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = voiceFieldNotes,
                onValueChange = { voiceFieldNotes = it },
                label = { Text("Field Notes (voice or type)") },
                trailingIcon = {
                    IconButton(onClick = { launchDictation() }) {
                        Icon(Icons.Default.Mic, contentDescription = "Dictate field notes", tint = PrimaryGreen)
                    }
                },
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                maxLines = 6
            )
            if (dictationUnavailable) {
                Text(
                    "Voice dictation isn't available on this device. Type your field notes instead.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("Photos (${photoUris.size})", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BackgroundCard)
                            .clickable { launchCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Add photo", tint = PrimaryGreen)
                    }
                }
                items(photoUris) { uriString ->
                    Box(modifier = Modifier.size(84.dp)) {
                        AsyncImage(
                            model = Uri.parse(uriString),
                            contentDescription = "Inspection photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(ErrorRed.copy(alpha = 0.85f))
                                .clickable { photoUris = photoUris - uriString },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (voiceFieldNotes.isBlank() && photoUris.isEmpty()) {
                        draftMessage = "Add field notes or a photo before drafting the report."
                        return@Button
                    }
                    isDrafting = true
                    draftMessage = null
                    scope.launch {
                        val photoAnalyses = photoUris.take(5).mapNotNull { uriString ->
                            runCatching {
                                PhotoAIHelper.analyzePhotoForFormFilling(context, Uri.parse(uriString))
                            }.getOrNull()
                        }
                        val draft = HybridAIService.draftInspectionReport(
                            fieldNotes = voiceFieldNotes,
                            photoAnalyses = photoAnalyses,
                            inspectionType = selectedType.name
                        )
                        findings = draft.findings
                        recommendations = draft.recommendations
                        selectedSeverity = FindingSeverity.entries
                            .firstOrNull { it.name.equals(draft.severity, ignoreCase = true) }
                            ?: selectedSeverity
                        speciesIdentified = draft.speciesIdentified
                        entryPoints = draft.entryPoints
                        damageAssessment = draft.damageAssessment
                        aiReportSource = draft.source
                        draftMessage = "AI draft ready (${if (draft.source == "grok") "live model" else "on-device"}). Review every field below before saving."
                        isDrafting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDrafting,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceBright, contentColor = TextPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isDrafting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TextPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isDrafting) "Drafting report..." else "AI Draft Report", fontWeight = FontWeight.Bold)
            }
            draftMessage?.let {
                Text(it, color = if (aiReportSource.isNotBlank()) PrimaryGreen else TextTertiary, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(color = BorderDark)

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
                                photos = photoUris,
                                voiceFieldNotes = voiceFieldNotes,
                                aiReportSource = aiReportSource,
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
                            photos = photoUris,
                            voiceFieldNotes = voiceFieldNotes,
                            aiReportSource = aiReportSource,
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

private fun createInspectionPhotoUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = File(context.filesDir, "inspection_photos").apply { mkdirs() }
    val file = File(storageDir, "INSPECT_${timeStamp}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}
