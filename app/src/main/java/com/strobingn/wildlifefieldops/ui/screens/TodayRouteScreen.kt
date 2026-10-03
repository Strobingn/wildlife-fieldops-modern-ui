package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.RouteStop
import com.strobingn.wildlifefieldops.ai.fieldops.TodayRouteEngine
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.TodayRouteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayRouteScreen(
    onBack: () -> Unit,
    viewModel: TodayRouteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val computed by viewModel.stops.collectAsState()
    var manual by remember { mutableStateOf<List<RouteStop>?>(null) }
    var origin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) origin = lastKnownLatLng(context)
    }
    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED) {
            origin = lastKnownLatLng(context)
        } else {
            permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val shown = manual ?: TodayRouteEngine.order(computed, origin?.first, origin?.second)
    val manualWins = shown.any { it.routeIndex != null } || manual != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's route", color = TextPrimary) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (manualWins) "Manual order is saved for today."
                else "Ordered by distance from your current GPS. Drag a stop to take over the order.",
                color = TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val url = TodayRouteEngine.multiMapsUrl(shown)
                        if (url.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    enabled = shown.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Navigate all") }
                OutlinedButton(onClick = {
                    manual = null
                    viewModel.clearManualOrder()
                }) { Text("Use distance") }
            }
            if (shown.isEmpty()) {
                Text("No jobs or trap checks scheduled for today.", color = TextSecondary)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(shown, key = { _, stop -> stop.id }) { index, stop ->
                    RouteStopRow(
                        index = index,
                        stop = stop,
                        onDragBy = { deltaRows ->
                            if (deltaRows == 0) return@RouteStopRow
                            val from = index
                            val to = (index + deltaRows).coerceIn(0, shown.lastIndex)
                            val next = TodayRouteEngine.reorder(shown, from, to)
                            manual = next
                            viewModel.persist(next)
                        },
                        onNavigate = {
                            val url = TodayRouteEngine.singleMapsUrl(stop)
                            if (url.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteStopRow(
    index: Int,
    stop: RouteStop,
    onDragBy: (Int) -> Unit,
    onNavigate: () -> Unit
) {
    val density = LocalDensity.current
    var accumulated by remember(stop.id) { mutableStateOf(0f) }
    Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = TextSecondary,
                modifier = Modifier.pointerInput(stop.id, index) {
                    detectDragGestures(
                        onDragEnd = { accumulated = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            accumulated += amount.y
                            val row = with(density) { 72.dp.toPx() }
                            val steps = (accumulated / row).toInt()
                            if (steps != 0) {
                                accumulated -= steps * row
                                onDragBy(steps)
                            }
                        }
                    )
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("${index + 1}. ${stop.title}", color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    listOf(
                        if (stop.kind == TodayRouteEngine.KIND_TRAP) "Trap check" else stop.statusLabel.ifBlank { "Job" },
                        stop.address
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    color = TextSecondary
                )
            }
            IconButton(onClick = onNavigate) {
                Icon(Icons.Default.Navigation, contentDescription = "Navigate", tint = PrimaryGreen)
            }
        }
    }
}

private fun lastKnownLatLng(context: Context): Pair<Double, Double>? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    val location = providers.firstNotNullOfOrNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    } ?: return null
    return location.latitude to location.longitude
}
