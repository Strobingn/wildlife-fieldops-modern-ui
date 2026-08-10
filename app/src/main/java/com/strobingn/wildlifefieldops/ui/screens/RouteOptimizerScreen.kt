package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.strobingn.wildlifefieldops.ui.theme.*
import kotlin.math.*

/**
 * Grok_Field_App_V2.5 — Enhanced Route Optimizer
 * - Nearest-neighbor + 2-opt local search
 * - Greyscale modern UI
 * - Live distance / time estimates
 * - Start-from-current + shuffle + clear
 * - Ready for real Job list injection (demo stops for now)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteOptimizerScreen(
    onBack: () -> Unit
) {
    var startLocation by remember { mutableStateOf("Current GPS / Yard") }
    var routeStops by remember {
        mutableStateOf(
            listOf(
                RouteStop("1", "123 Main St, Middletown, NY", LatLng(41.4459, -74.4229), "Raccoon removal", 45),
                RouteStop("2", "456 Oak Ave, Goshen, NY", LatLng(41.4032, -74.3243), "Squirrel inspection", 30),
                RouteStop("3", "789 Pine Rd, Warwick, NY", LatLng(41.2565, -74.3599), "Bat exclusion", 90),
                RouteStop("4", "321 Elm Dr, Monroe, NY", LatLng(41.3306, -74.1868), "Follow-up visit", 25),
                RouteStop("5", "55 River Rd, Newburgh, NY", LatLng(41.5034, -74.0104), "Skunk trapping", 60)
            )
        )
    }
    var isOptimized by remember { mutableStateOf(false) }
    var optimizationPasses by remember { mutableStateOf(0) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.40, -74.20), 10.2f)
    }

    val totalDistance = remember(routeStops) { calculateTotalDistance(routeStops) }
    val estimatedDriveMin = remember(totalDistance) { (totalDistance / 32.0 * 60).toInt().coerceAtLeast(5) } // ~32 mph field avg
    val serviceMin = remember(routeStops) { routeStops.sumOf { it.serviceMinutes } }
    val totalMin = estimatedDriveMin + serviceMin

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Route Optimizer", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            if (isOptimized) "Optimized · $optimizationPasses passes" else "Unoptimized order",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
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
        ) {
            // Map
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                cameraPositionState = cameraPositionState
            ) {
                routeStops.forEachIndexed { index, stop ->
                    Marker(
                        state = MarkerState(position = stop.latLng),
                        title = "${index + 1}. ${stop.jobDescription}",
                        snippet = stop.address
                    )
                }
                if (routeStops.size > 1) {
                    Polyline(
                        points = routeStops.map { it.latLng },
                        color = PrimaryGreen,
                        width = 7f
                    )
                }
            }

            // Stats row — greyscale modern cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteStatCard(
                    value = "${routeStops.size}",
                    label = "Stops",
                    modifier = Modifier.weight(1f)
                )
                RouteStatCard(
                    value = String.format("%.1f", totalDistance),
                    label = "Miles",
                    modifier = Modifier.weight(1f)
                )
                RouteStatCard(
                    value = "$estimatedDriveMin",
                    label = "Drive min",
                    modifier = Modifier.weight(1f)
                )
                RouteStatCard(
                    value = "$totalMin",
                    label = "Total min",
                    modifier = Modifier.weight(1f)
                )
            }

            // Start location
            OutlinedTextField(
                value = startLocation,
                onValueChange = { startLocation = it },
                label = { Text("Start / Yard") },
                leadingIcon = {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = TextSecondary)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BackgroundCard,
                    unfocusedContainerColor = BackgroundCard,
                    focusedLabelColor = TextSecondary,
                    unfocusedLabelColor = TextTertiary,
                    cursorColor = PrimaryGreen
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val (optimized, passes) = optimizeRouteAdvanced(routeStops)
                        routeStops = optimized
                        isOptimized = true
                        optimizationPasses = passes
                    },
                    modifier = Modifier.weight(1.4f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOptimized) SurfaceBright else PrimaryGreen,
                        contentColor = if (isOptimized) TextPrimary else Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        if (isOptimized) Icons.Default.CheckCircle else Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isOptimized) "Re-optimize" else "Optimize Route",
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = {
                        routeStops = routeStops.shuffled()
                        isOptimized = false
                        optimizationPasses = 0
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(18.dp))
                }
                OutlinedButton(
                    onClick = {
                        routeStops = emptyList()
                        isOptimized = false
                        optimizationPasses = 0
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextTertiary),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
            }

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Route sequence",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${routeStops.size} stops · ~$totalMin min",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(routeStops, key = { _, stop -> stop.id }) { index, stop ->
                    RouteStopCard(
                        stop = stop,
                        index = index + 1,
                        isFirst = index == 0,
                        isLast = index == routeStops.lastIndex,
                        onRemove = {
                            routeStops = routeStops.filter { it.id != stop.id }
                            isOptimized = false
                        }
                    )
                }
                item {
                    // Add stop hint
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = TextTertiary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Add from Jobs / GPS", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Text("Wire to JobRepository + current location in next pass", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

data class RouteStop(
    val id: String,
    val address: String,
    val latLng: LatLng,
    val jobDescription: String,
    val serviceMinutes: Int = 30
)

private fun haversineMiles(a: LatLng, b: LatLng): Double {
    val R = 3958.8 // Earth radius miles
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * R * asin(sqrt(h))
}

private fun calculateTotalDistance(stops: List<RouteStop>): Double {
    if (stops.size < 2) return 0.0
    var total = 0.0
    for (i in 0 until stops.size - 1) {
        total += haversineMiles(stops[i].latLng, stops[i + 1].latLng)
    }
    return total
}

/** Nearest neighbor seed + 2-opt improvement. Returns (route, improvementPasses). */
private fun optimizeRouteAdvanced(stops: List<RouteStop>): Pair<List<RouteStop>, Int> {
    if (stops.size < 3) return stops to 0

    // 1. Nearest neighbor seed
    val remaining = stops.toMutableList()
    val tour = mutableListOf(remaining.removeAt(0))
    while (remaining.isNotEmpty()) {
        val last = tour.last()
        val nearestIdx = remaining.indices.minByOrNull { haversineMiles(last.latLng, remaining[it].latLng) }!!
        tour.add(remaining.removeAt(nearestIdx))
    }

    // 2. 2-opt local search
    var improved = true
    var passes = 0
    val maxPasses = 40
    while (improved && passes < maxPasses) {
        improved = false
        passes++
        for (i in 0 until tour.size - 2) {
            for (k in i + 2 until tour.size) {
                val a = tour[i].latLng
                val b = tour[i + 1].latLng
                val c = tour[k].latLng
                val d = if (k + 1 < tour.size) tour[k + 1].latLng else null

                val current = haversineMiles(a, b) + (if (d != null) haversineMiles(c, d) else 0.0)
                val swapped = haversineMiles(a, c) + (if (d != null) haversineMiles(b, d) else 0.0)

                if (swapped + 0.0001 < current) {
                    // reverse the segment i+1 .. k
                    val sub = tour.subList(i + 1, k + 1).toList().reversed()
                    for (j in sub.indices) {
                        tour[i + 1 + j] = sub[j]
                    }
                    improved = true
                }
            }
        }
    }
    return tour to passes
}

@Composable
private fun RouteStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun RouteStopCard(
    stop: RouteStop,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sequence badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isFirst) PrimaryGreen.copy(alpha = 0.25f) else SurfaceBright),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    index.toString(),
                    color = if (isFirst) PrimaryGreen else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stop.jobDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stop.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    "~${stop.serviceMinutes} min service",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
