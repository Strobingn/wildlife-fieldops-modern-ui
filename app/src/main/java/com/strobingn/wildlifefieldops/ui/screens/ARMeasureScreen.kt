package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.ARMeasurementHelper
import com.strobingn.wildlifefieldops.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ARMeasureScreen(
    jobId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<ARMeasurementHelper.MeasurementResult?>(null) }
    val supported = remember { ARMeasurementHelper.isARCoreSupported(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR Measurement", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Job: $jobId", color = TextSecondary)
            Text(
                if (supported) "ARCore available — use for accurate entry-point / exclusion sizes"
                else "ARCore not supported on this device — using simulation mode",
                color = TextPrimary
            )

            Button(
                onClick = {
                    result = ARMeasurementHelper.simulateMeasurementForDemo(0.45f)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Straighten, null)
                Spacer(Modifier.width(8.dp))
                Text("Measure Entry / Damage Point", fontWeight = FontWeight.Bold)
            }

            result?.let { r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Distance: ${"%.2f".format(r.distanceMeters)} m", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Confidence: ${(r.confidence * 100).toInt()}%", color = TextSecondary)
                        Text(r.notes, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                        Text("Plane: ${r.planeType}", color = AccentCyan)
                    }
                }
            }
        }
    }
}
