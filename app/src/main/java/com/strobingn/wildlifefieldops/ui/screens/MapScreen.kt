package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.R
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.MapViewModel
import com.google.maps.android.compose.MapType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onNavigateToJobDetail: (String) -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val properties by viewModel.filteredProperties.collectAsState()
    val unlocatedJobCount by viewModel.unlocatedJobCount.collectAsState()
    val isDrawing by viewModel.isDrawingBoundary.collectAsState()
    val boundaryPoints by viewModel.boundaryPoints.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val cachedCamera by viewModel.cachedCamera.collectAsState()
    val cachedRegion by viewModel.cachedRegion.collectAsState()
    val hasCachedTiles by viewModel.hasCachedTiles.collectAsState()
    val isCaching by viewModel.isCaching.collectAsState()
    val cacheProgress by viewModel.cacheProgress.collectAsState()
    val cacheMessage by viewModel.cacheMessage.collectAsState()
    val observations by viewModel.observations.collectAsState()
    val isObserving by viewModel.isObserving.collectAsState()
    val pendingPin by viewModel.pendingPin.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val mapApiKeyConfigured = BuildConfig.GOOGLE_MAPS_API_KEY.trim().let { key ->
        key.isNotEmpty() && !key.contains("YOUR_", ignoreCase = true)
    }
    val hasLocationPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val mapStyleOptions = remember(context) {
        runCatching { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_grayscale) }.getOrNull()
    }

    var showSearch by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var selectedStatus by remember { mutableStateOf<JobStatus?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }
    var hasAutoFitted by remember { mutableStateOf(false) }
    var mapInitializationTimedOut by remember { mutableStateOf(false) }
    var observationNotes by remember { mutableStateOf("") }
    var observationSpecies by remember { mutableStateOf("") }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingPhotoPath = tempPhotoFile?.absolutePath
            pendingPhotoUri = tempPhotoFile?.let { file ->
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
        }
    }

    val visibleProperties by remember(properties, selectedStatus) {
        derivedStateOf {
            properties.filter { selectedStatus == null || it.status == selectedStatus }
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        val cam = cachedCamera
        position = if (cam != null) {
            CameraPosition.fromLatLngZoom(LatLng(cam.latitude, cam.longitude), cam.zoom)
        } else {
            CameraPosition.fromLatLngZoom(LatLng(41.45, -74.05), 12f)
        }
    }

    LaunchedEffect(cachedCamera) {
        val cam = cachedCamera ?: return@LaunchedEffect
        if (!hasAutoFitted && properties.isEmpty()) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(cam.latitude, cam.longitude), cam.zoom
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshConnectivity()
        snapshotFlow {
            val p = cameraPositionState.position
            Triple(p.target.latitude, p.target.longitude, p.zoom)
        }
            .distinctUntilChanged()
            .collect { (lat, lng, zoom) ->
                delay(750)
                val cur = cameraPositionState.position
                viewModel.persistCamera(cur.target.latitude, cur.target.longitude, cur.zoom)
            }
    }

    LaunchedEffect(cacheMessage) {
        val msg = cacheMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearCacheMessage()
    }

    fun fitVisibleJobs() {
        if (visibleProperties.isEmpty()) return
        val bounds = LatLngBounds.Builder().apply {
            visibleProperties.forEach { include(LatLng(it.latitude, it.longitude)) }
        }.build()
        runCatching {
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 96))
        }
    }

    // Completed jobs may arrive after a sync. Fit once when the first complete
    // set of located jobs is available so they are not hidden outside the
    // default Hudson Valley camera position.
    LaunchedEffect(mapLoaded, properties.size) {
        if (mapLoaded && properties.isNotEmpty() && !hasAutoFitted) {
            fitVisibleJobs()
            hasAutoFitted = true
        }
    }

    LaunchedEffect(mapApiKeyConfigured, mapLoaded) {
        if (!mapApiKeyConfigured || mapLoaded) return@LaunchedEffect
        delay(12_000)
        if (!mapLoaded) mapInitializationTimedOut = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!showSearch) {
                TopAppBar(
                    title = { Text("Property Map", color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                        }
                        IconButton(onClick = { showControls = !showControls }) {
                            Icon(
                                if (showControls) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = "Toggle Controls",
                                tint = TextSecondary
                            )
                        }
                        IconButton(onClick = { fitVisibleJobs() }) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit all jobs", tint = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark.copy(alpha = 0.9f))
                )
            }
        },
        containerColor = BackgroundDark
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isOffline || hasCachedTiles || cacheProgress != null) {
                val cameraInCache = cachedRegion?.contains(
                    cameraPositionState.position.target.latitude,
                    cameraPositionState.position.target.longitude
                ) == true
                val (bannerTitle, bannerBody, bannerColor) = when {
                    cacheProgress != null -> Triple(
                        "Caching service area",
                        cacheProgress ?: "",
                        AccentBlue
                    )
                    isOffline && hasCachedTiles && cameraInCache -> Triple(
                        "Offline · cached tiles",
                        "Showing cached map tiles + Room job/observation pins. © OSM © CARTO",
                        AccentBlue
                    )
                    isOffline && hasCachedTiles -> Triple(
                        "Offline · outside cached area",
                        "Pins still work from Room. Pan back to the cached region or cache this view when online.",
                        AccentAmber
                    )
                    isOffline -> Triple(
                        "Offline map mode",
                        "Showing Room job/observation pins + last camera. Tap Cache while online to store tiles.",
                        AccentBlue
                    )
                    else -> Triple(
                        "Map tiles cached",
                        "${cachedRegion?.tileCount ?: 0} tiles ready for offline use. © OSM © CARTO",
                        SurfaceVariant
                    )
                }
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .zIndex(2f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOffline) bannerColor.copy(alpha = 0.92f) else BackgroundCard.copy(alpha = 0.94f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (isOffline) Color.White else TextPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                bannerTitle,
                                color = if (isOffline) Color.White else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                bannerBody,
                                color = if (isOffline) Color.White.copy(alpha = 0.9f) else TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    mapType = MapType.NORMAL,
                    mapStyleOptions = mapStyleOptions
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = true,
                    compassEnabled = true,
                    mapToolbarEnabled = false
                ),
                onMapLoaded = {
                    mapLoaded = true
                    mapInitializationTimedOut = false
                },
                onMapClick = { latLng ->
                    viewModel.onMapTapped(latLng)
                }
            ) {
                if (isOffline && hasCachedTiles) {
                    TileOverlay(
                        tileProvider = viewModel.tileProvider,
                        fadeIn = false
                    )
                }

                // Property markers
                visibleProperties.forEach { property ->
                    key(property.id) {
                        Marker(
                            state = MarkerState(position = LatLng(property.latitude, property.longitude)),
                            title = "${property.name} · ${property.status.name.replace('_', ' ')}",
                            snippet = "${property.address} (${property.type})",
                            icon = remember(property.id, property.status) {
                                createMonochromeMarkerIcon(property.status)
                            },
                            onClick = {
                                onNavigateToJobDetail(property.id)
                                true
                            }
                        )
                    }
                }

                observations.forEach { observation ->
                    key(observation.id) {
                        Marker(
                            state = MarkerState(position = LatLng(observation.latitude, observation.longitude)),
                            title = observation.notes.ifBlank { "Field observation" },
                            snippet = buildString {
                                append(if (observation.isSynced) "Synced" else "Queued for sync")
                                if (observation.photoLocalPath.isNotBlank()) append(" · photo")
                                if (observation.speciesHint.isNotBlank()) append(" · ${observation.speciesHint}")
                            },
                            icon = remember(observation.id, observation.isSynced) {
                                createObservationMarkerIcon(observation.isSynced)
                            }
                        )
                    }
                }

                pendingPin?.let { pin ->
                    Marker(
                        state = MarkerState(position = pin),
                        title = "New observation",
                        snippet = "Add a note and optional photo",
                        icon = remember { createObservationMarkerIcon(false) }
                    )
                }

                // Boundary polygon
                if (boundaryPoints.size > 2) {
                    Polygon(
                        points = boundaryPoints,
                        fillColor = PrimaryGreen.copy(alpha = 0.15f),
                        strokeColor = PrimaryGreen,
                        strokeWidth = 3f
                    )
                }

                // Boundary points markers
                if (isDrawing) {
                    boundaryPoints.forEachIndexed { index, point ->
                        Marker(
                            state = MarkerState(position = point),
                            title = "Point ${index + 1}",
                            icon = createMonochromeMarkerIcon(JobStatus.IN_PROGRESS)
                        )
                    }
                }
            }

            if (!mapApiKeyConfigured || mapInitializationTimedOut) {
                Card(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            if (!mapApiKeyConfigured) "Google Maps is not configured" else "Google Maps did not initialize",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (!mapApiKeyConfigured) {
                                "Add the GOOGLE_MAPS_API repository secret and rebuild this branch."
                            } else {
                                "The key is present, but Android Maps did not initialize. Enable Maps SDK for Android and allow package com.strobingn.wildlifefieldops in Google Cloud."
                            },
                            color = TextSecondary
                        )
                    }
                }
            }

            // Search overlay
            if (showSearch) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(start = 8.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text("Search properties...", color = TextTertiary) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.Words)
                        )
                        IconButton(onClick = {
                            viewModel.setSearchQuery("")
                            showSearch = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }
                }
            }

            // Bottom Controls
            if (showControls && pendingPin == null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundCard.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Property count
                        Text(
                            "${visibleProperties.size} jobs · ${observations.size} observations · $unlocatedJobCount without coordinates",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedStatus == null,
                                onClick = { selectedStatus = null },
                                label = { Text("All") }
                            )
                            JobStatus.entries.forEach { status ->
                                FilterChip(
                                    selected = selectedStatus == status,
                                    onClick = { selectedStatus = if (selectedStatus == status) null else status },
                                    label = { Text(status.name.replace('_', ' ')) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MapControlButton(
                                label = if (isDrawing) "Drawing..." else "Boundary",
                                icon = if (isDrawing) Icons.Default.Edit else Icons.Default.Gesture,
                                active = isDrawing,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.toggleDrawingMode() }
                            )

                            MapControlButton(
                                label = "Save",
                                icon = Icons.Default.Save,
                                active = false,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.saveBoundary() }
                            )

                            MapControlButton(
                                label = "Clear",
                                icon = Icons.Default.ClearAll,
                                active = false,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.clearBoundary() }
                            )

                            MapControlButton(
                                label = "Fit Jobs",
                                icon = Icons.Default.CenterFocusStrong,
                                active = false,
                                modifier = Modifier.weight(1f),
                                onClick = { fitVisibleJobs() }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MapControlButton(
                                label = if (isObserving) "Pinning…" else "Observe",
                                icon = Icons.Default.AddLocationAlt,
                                active = isObserving,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.toggleObserveMode() }
                            )

                            MapControlButton(
                                label = if (isCaching) "Caching" else "Cache",
                                icon = Icons.Default.CloudDownload,
                                active = isCaching,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isCaching) return@MapControlButton
                                    val p = cameraPositionState.position
                                    viewModel.persistCamera(p.target.latitude, p.target.longitude, p.zoom)
                                    val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
                                    if (bounds != null) {
                                        viewModel.snapshotOfflineCache(
                                            bounds.southwest.latitude,
                                            bounds.southwest.longitude,
                                            bounds.northeast.latitude,
                                            bounds.northeast.longitude,
                                            p.zoom.toInt()
                                        )
                                    } else {
                                        viewModel.snapshotOfflineCache()
                                    }
                                }
                            )
                        }

                        // Legend
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LegendDot("Pending", StatusPending)
                            LegendDot("Active", AccentBlue)
                            LegendDot("Done", SuccessGreen)
                            LegendDot("Cancelled", ErrorRed)
                            LegendDot("Observe", AccentAmber)
                        }
                    }
                }
            }

            if (pendingPin != null) {
                ObservationComposerCard(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .zIndex(3f),
                    notes = observationNotes,
                    onNotesChange = { observationNotes = it },
                    species = observationSpecies,
                    onSpeciesChange = { observationSpecies = it },
                    photoPath = pendingPhotoPath,
                    onTakePhoto = {
                        val file = createObservationPhotoFile(context)
                        tempPhotoFile = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        cameraLauncher.launch(uri)
                    },
                    onCancel = {
                        observationNotes = ""
                        observationSpecies = ""
                        pendingPhotoPath = null
                        pendingPhotoUri = null
                        viewModel.cancelPendingObservation()
                    },
                    onSave = {
                        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        val lastKnown = locationManager?.let { viewModel.lastKnownLocation(it) }
                        viewModel.saveObservation(
                            notes = observationNotes,
                            photoLocalPath = pendingPhotoPath,
                            photoUri = pendingPhotoUri?.toString(),
                            speciesHint = observationSpecies,
                            lastKnown = lastKnown
                        )
                        observationNotes = ""
                        observationSpecies = ""
                        pendingPhotoPath = null
                        pendingPhotoUri = null
                    }
                )
            }

            if (isObserving && pendingPin == null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (showSearch || isOffline || hasCachedTiles) 80.dp else 16.dp)
                        .padding(horizontal = 16.dp)
                        .zIndex(2f),
                    colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Tap the map to drop an observation pin",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Drawing mode indicator
            if (isDrawing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (showSearch) 80.dp else 16.dp)
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusPending.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Tap map to add boundary points (${boundaryPoints.size} set)",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapControlButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (active) PrimaryGreen.copy(alpha = 0.2f) else SurfaceVariant
    val contentColor = if (active) PrimaryGreen else TextSecondary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}

@Composable
private fun ObservationComposerCard(
    modifier: Modifier = Modifier,
    notes: String,
    onNotesChange: (String) -> Unit,
    species: String,
    onSpeciesChange: (String) -> Unit,
    photoPath: String?,
    onTakePhoto: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BackgroundCard.copy(alpha = 0.97f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("New field observation", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Saved to Room immediately. Syncs with Supabase when you are online.",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What did you see?", color = TextTertiary) },
                singleLine = false,
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark
                )
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = species,
                onValueChange = onSpeciesChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Species hint (optional)", color = TextTertiary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (photoPath.isNullOrBlank()) "No photo attached" else "Photo ready: ${File(photoPath).name}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Photo")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Save")
                }
            }
        }
    }
}

private fun createObservationPhotoFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = File(context.filesDir, "photos").apply { mkdirs() }
    return File(storageDir, "OBS_$timeStamp.jpg")
}
