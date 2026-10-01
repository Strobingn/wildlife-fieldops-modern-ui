package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.strobingn.wildlifefieldops.ui.theme.*
import kotlinx.coroutines.delay
import com.google.maps.android.compose.MapType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GPSScreen(
    onNavigateToMap: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var trackingEnabled by remember { mutableStateOf(false) }
    var gpsAccuracy by remember { mutableStateOf(0f) }
    var speed by remember { mutableStateOf(0f) }
    var altitude by remember { mutableStateOf(0.0) }
    var satellites by remember { mutableStateOf(0) }
    var totalDistance by remember { mutableStateOf(0.0) }
    var trackPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var elapsedTime by remember { mutableStateOf(0L) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.45, -74.05), 15f)
    }

    // Location updates using Android LocationManager
    DisposableEffect(trackingEnabled) {
        var locationManager: LocationManager? = null
        var listener: LocationListener? = null

        if (trackingEnabled) {
            try {
                locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val newLatLng = LatLng(location.latitude, location.longitude)
                        currentLocation?.let { prev ->
                            val results = FloatArray(1)
                            Location.distanceBetween(prev.latitude, prev.longitude, newLatLng.latitude, newLatLng.longitude, results)
                            totalDistance += results[0].toDouble()
                        }
                        currentLocation = newLatLng
                        gpsAccuracy = location.accuracy
                        speed = location.speed * 2.23694f // m/s to mph
                        altitude = location.altitude
                        trackPoints = trackPoints + newLatLng
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(newLatLng, 17f)
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        onDispose {
            listener?.let { locationManager?.removeUpdates(it) }
        }
    }

    // Timer
    LaunchedEffect(trackingEnabled) {
        if (trackingEnabled) {
            val startTime = System.currentTimeMillis() - elapsedTime
            while (trackingEnabled) {
                elapsedTime = System.currentTimeMillis() - startTime
                delay(1000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS Tracking", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMap) {
                        Icon(Icons.Default.Map, contentDescription = "Full Map", tint = TextSecondary)
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
        ) {
            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatPill("${String.format("%.1f", speed)} mph", Icons.Default.Speed, AccentBlue, Modifier.weight(1f))
                StatPill("${String.format("%.0f", totalDistance)} ft", Icons.Default.Straighten, PrimaryGreen, Modifier.weight(1f))
                StatPill(formatElapsedTime(elapsedTime), Icons.Default.Timer, AccentPurple, Modifier.weight(1f))
            }

            // Map
            Box(modifier = Modifier.weight(1f)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = true,
                        mapType = MapType.NORMAL
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = true,
                        compassEnabled = true
                    )
                ) {
                    currentLocation?.let { location ->
                        Marker(
                            state = MarkerState(position = location),
                            title = "Current Location",
                            snippet = "${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}"
                        )
                    }

                    if (trackPoints.size > 1) {
                        Polyline(
                            points = trackPoints,
                            color = PrimaryGreen,
                            width = 6f
                        )
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        trackingEnabled = !trackingEnabled
                        if (!trackingEnabled) {
                            elapsedTime = 0
                            trackPoints = emptyList()
                            totalDistance = 0.0
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (trackingEnabled) ErrorRed else PrimaryGreen,
                        contentColor = if (trackingEnabled) Color.White else OnPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (trackingEnabled) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (trackingEnabled) "Stop Tracking" else "Start Tracking", fontWeight = FontWeight.Bold)
                }

                if (trackPoints.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            trackPoints = emptyList()
                            totalDistance = 0.0
                            elapsedTime = 0
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BackgroundCard)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

private fun formatElapsedTime(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
}
