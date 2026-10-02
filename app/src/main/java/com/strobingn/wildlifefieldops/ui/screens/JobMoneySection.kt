package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.JobTimer
import com.strobingn.wildlifefieldops.ai.fieldops.ProfitResult
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.MoneyFieldOpsViewModel

@Composable
fun JobMoneySection(
    job: Job,
    moneyVm: MoneyFieldOpsViewModel
) {
    val tick by moneyVm.tick.collectAsState()
    val timer = moneyVm.timerState(job)
    val display = JobTimer.format(timer.displayedMs(tick.takeIf { it > 0L } ?: System.currentTimeMillis()))
    var minutesText by remember(job.pricing.timerElapsedMs) {
        mutableStateOf((job.pricing.timerElapsedMs / 60_000L).toString())
    }
    var materialsText by remember(job.pricing.materialsCostActual) {
        mutableStateOf(job.pricing.materialsCostActual.takeIf { it > 0 }?.toString().orEmpty())
    }
    var laborText by remember(job.pricing.laborCostOverride) {
        mutableStateOf(job.pricing.laborCostOverride?.toString().orEmpty())
    }
    var paidText by remember(job.pricing.paidAmount) {
        mutableStateOf(job.pricing.paidAmount.takeIf { it > 0 }?.toString().orEmpty())
    }
    var profit by remember { mutableStateOf<ProfitResult?>(null) }
    LaunchedEffect(job.id, job.updatedAt, job.pricing) {
        profit = moneyVm.profit(job)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("On-site timer", color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(display, style = MaterialTheme.typography.headlineSmall, color = PrimaryGreen, fontWeight = FontWeight.Bold)
            Text(
                if (timer.running) "Running — stop to write the visit." else "Start on arrival. Elapsed minutes stay editable.",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (timer.running) {
                    Button(
                        onClick = { moneyVm.stopTimer(job.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                    ) { Text("Stop") }
                } else {
                    Button(
                        onClick = { moneyVm.startTimer(job.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                    ) { Text("Start") }
                }
            }
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Elapsed minutes") },
                modifier = Modifier.fillMaxWidth(),
                colors = moneyFieldColors()
            )
            OutlinedButton(onClick = {
                moneyVm.setMinutes(job.id, minutesText.toLongOrNull() ?: 0L)
            }) { Text("Save minutes") }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Profit", color = TextPrimary, fontWeight = FontWeight.Medium)
            val p = profit
            if (p != null) {
                Text(
                    "Revenue $${"%.2f".format(p.revenue)} · Cost $${"%.2f".format(p.totalCost)}",
                    color = TextSecondary
                )
                Text(
                    "Profit $${"%.2f".format(p.profit)}  (${"%.1f".format(p.marginPercent)}%)",
                    color = if (p.profit >= 0) PrimaryGreen else TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedTextField(value = materialsText, onValueChange = { materialsText = it }, label = { Text("Materials cost") }, modifier = Modifier.fillMaxWidth(), colors = moneyFieldColors())
            OutlinedTextField(value = laborText, onValueChange = { laborText = it }, label = { Text("Labor cost override") }, modifier = Modifier.fillMaxWidth(), colors = moneyFieldColors())
            OutlinedTextField(value = paidText, onValueChange = { paidText = it }, label = { Text("Amount paid") }, modifier = Modifier.fillMaxWidth(), colors = moneyFieldColors())
            Button(
                onClick = {
                    moneyVm.saveProfit(
                        job.id,
                        materialsText.toDoubleOrNull() ?: 0.0,
                        laborText.toDoubleOrNull(),
                        paidText.toDoubleOrNull() ?: 0.0
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) { Text("Save costs") }
        }
    }
}

@Composable
private fun moneyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
