package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.strobingn.wildlifefieldops.ui.viewmodel.MapProperty
import com.strobingn.wildlifefieldops.ui.viewmodel.MapViewModel
import com.google.maps.android.compose.MapType


private fun createMonochromeMarkerIcon(status: JobStatus): com.google.android.gms.maps.model.BitmapDescriptor {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (status) {
            JobStatus.PENDING -> android.graphics.Color.rgb(170, 170, 170)
            JobStatus.IN_PROGRESS -> android.graphics.Color.rgb(115, 115, 115)
            JobStatus.COMPLETED -> android.graphics.Color.rgb(55, 55, 55)
            JobStatus.INVOICED -> android.graphics.Color.rgb(80, 80, 80)
            JobStatus.PAID -> android.graphics.Color.rgb(25, 25, 25)
            JobStatus.CANCELLED -> android.graphics.Color.rgb(145, 145, 145)
        }
    }
    canvas.drawCircle(size / 2f, size / 2f, size * 0.32f, paint)
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
}

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

    val visibleProperties = remember(properties, selectedStatus) {
        properties.filter { selectedStatus == null || it.status == selectedStatus }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.45, -74.05), 12f)
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

    Scaffold(
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
                onMapLoaded = { mapLoaded = true },
                onMapClick = { latLng ->
                    if (isDrawing) {
                        viewModel.addBoundaryPoint(latLng)
                    }
                }
            ) {
                // Property markers
                visibleProperties.forEach { property ->
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

            if (!mapApiKeyConfigured) {
                Card(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Google Maps is not configured", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Add the GOOGLE_MAPS_API repository secret and rebuild this branch.", color = TextSecondary)
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
            if (showControls) {
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
                            "${visibleProperties.size} jobs shown · ${unlocatedJobCount} without coordinates",
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

                            MapControlButton(
                                label = "Snapshot",
                                icon = Icons.Default.CameraAlt,
                                active = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // Save map snapshot functionality
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
                        }
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
