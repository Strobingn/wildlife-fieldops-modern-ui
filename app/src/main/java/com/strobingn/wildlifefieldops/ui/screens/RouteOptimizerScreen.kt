package com.strobingn.wildlifefieldops.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.route.RouteOptimizationEngine
import com.strobingn.wildlifefieldops.data.route.RoutePoint
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteOptimizerScreen(
    onBack: () -> Unit,
    viewModel: JobsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val jobs by viewModel.jobs.collectAsState()
    LaunchedEffect(jobs) { viewModel.fillMissingCoordinates() }
    val sourceStops = remember(jobs) {
        jobs.asSequence()
            .filter { it.status != JobStatus.COMPLETED && it.status != JobStatus.CANCELLED }
            .filter { isTodayOrOpen(it) }
            .filter { it.latitude != null && it.longitude != null }
            .sortedWith(compareBy<Job> { it.scheduledDate ?: Long.MAX_VALUE }.thenByDescending { priorityRank(it.priority) })
            .map { it.toRouteStop() }
            .toList()
    }
    var routeStops by remember(sourceStops) { mutableStateOf(sourceStops) }
    var isOptimized by rememberSaveable { mutableStateOf(false) }
    var returnToStart by rememberSaveable { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.50, -74.20), 9.5f)
    }
    val totalDistance = RouteOptimizationEngine.totalDistanceMiles(routeStops.map { it.toRoutePoint() }, returnToStart)
    val travelMinutes = (totalDistance / 35.0 * 60.0).roundToInt()
    val serviceMinutes = routeStops.size * 45
    val totalMinutes = travelMinutes + serviceMinutes
    val missingCoordinates = jobs.count {
        it.status != JobStatus.COMPLETED && it.status != JobStatus.CANCELLED &&
            isTodayOrOpen(it) && (it.latitude == null || it.longitude == null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Route planner", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isOptimized) "Optimized local driving order" else "Today's jobs with map pins",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (routeStops.isEmpty()) {
            RouteEmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                missingCoordinates = missingCoordinates
            )
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(230.dp),
                cameraPositionState = cameraPositionState
            ) {
                routeStops.forEachIndexed { index, stop ->
                    Marker(
                        state = MarkerState(position = LatLng(stop.latitude, stop.longitude)),
                        title = "${index + 1}. ${stop.address}",
                        snippet = "${stop.serviceType} • ${stop.priorityLabel}"
                    )
                }
                if (routeStops.size > 1) {
                    Polyline(
                        points = routeStops.map { LatLng(it.latitude, it.longitude) },
                        color = MaterialTheme.colorScheme.primary,
                        width = 5f
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RouteStat(routeStops.size.toString(), "Stops")
                    RouteStat(String.format("%.1f", totalDistance), "Miles")
                    RouteStat("$totalMinutes", "Minutes")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        routeStops = RouteOptimizationEngine.optimize(routeStops.map { it.toRoutePoint() })
                            .mapNotNull { point -> routeStops.firstOrNull { it.id == point.id } }
                        isOptimized = true
                    },
                    enabled = routeStops.size > 2,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Route, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isOptimized) "Re-optimize" else "Optimize")
                }
                OutlinedButton(
                    onClick = { routeStops = sourceStops; isOptimized = false },
                    enabled = routeStops != sourceStops,
                    shape = RoundedCornerShape(14.dp)
                ) { Icon(Icons.Default.RestartAlt, contentDescription = "Reset") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = returnToStart, onCheckedChange = { returnToStart = it })
                Text("Include return to first stop", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Starts at ${routeStops.first().address}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { openGoogleMaps(context, routeStops) }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Default.Navigation, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate")
                }
            }
            Text("Stops", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(routeStops, key = { it.id }) { stop ->
                    RouteStopCard(stop, routeStops.indexOf(stop) + 1) {
                        routeStops = routeStops.filter { it.id != stop.id }
                        isOptimized = false
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

private data class RouteStop(
    val id: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val serviceType: String,
    val priorityLabel: String,
    val scheduledDate: Long?
)

private fun Job.toRouteStop() = RouteStop(
    id, address.ifBlank { customerName.ifBlank { "Job location" } },
    latitude ?: 0.0, longitude ?: 0.0,
    type.ifBlank { title.ifBlank { "Wildlife service" } },
    priority.name, scheduledDate
)

private fun RouteStop.toRoutePoint() = RoutePoint(id, latitude, longitude)

private fun isTodayOrOpen(job: Job): Boolean {
    val start = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = start + 24L * 60 * 60 * 1000
    val scheduled = job.scheduledDate
    return if (scheduled != null) scheduled in start until end
    else job.status == JobStatus.PENDING || job.status == JobStatus.IN_PROGRESS
}

private fun priorityRank(priority: JobPriority): Int = when (priority) {
    JobPriority.URGENT -> 4
    JobPriority.HIGH -> 3
    JobPriority.MEDIUM -> 2
    JobPriority.LOW -> 1
}

private fun openGoogleMaps(context: Context, stops: List<RouteStop>) {
    val destination = Uri.encode(stops.last().address)
    val waypoints = stops.dropLast(1).joinToString("|") { Uri.encode(it.address) }
    val waypointQuery = if (waypoints.isBlank()) "" else "&waypoints=$waypoints"
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
        "https://www.google.com/maps/dir/?api=1&destination=$destination&travelmode=driving$waypointQuery"
    )))
}

@Composable
private fun RouteStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RouteStopCard(stop: RouteStop, index: Int, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                Text(index.toString(), Modifier.padding(horizontal = 11.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stop.address, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${stop.serviceType} • ${stop.priorityLabel.lowercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                stop.scheduledDate?.let {
                    Text("Scheduled " + java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove stop", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RouteEmptyState(modifier: Modifier, missingCoordinates: Int) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Route, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Looking up today's stops", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (missingCoordinates > 0) {
                "$missingCoordinates job(s) have an address but no pin yet. The app is looking those up now. Leave this screen open a few seconds, then come back."
            } else {
                "Jobs scheduled today with a street address will show here after the pin lookup finishes."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
