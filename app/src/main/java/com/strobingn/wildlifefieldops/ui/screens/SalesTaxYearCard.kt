package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.SalesTaxYear
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import java.util.Locale

private fun usd(v: Double) = "$" + String.format(Locale.US, "%,.2f", v)

/**
 * Top of the Tax tab: the automatic NY sales tax grand total for the year,
 * quarter subtotals, and sales by jurisdiction and rate (ST-100 / ST-101).
 * Every total can be typed over (Lock) and returned to calculated (Unlock).
 */
@Composable
fun SalesTaxYearCard(
    summary: SalesTaxYear.Summary,
    onKind: (SalesTaxYear.Kind) -> Unit,
    onShiftYear: (Int) -> Unit,
    onLock: (field: String, raw: String) -> Unit,
    onShareCsv: () -> Unit,
    onSharePdf: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NY sales tax for the year", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                "Added up automatically from every invoice and job, plus hand adjustments. Use this for your NYS sales tax return.",
                color = TextSecondary, style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SalesTaxYear.Kind.entries.forEach { k ->
                    FilterChip(
                        selected = summary.kind == k,
                        onClick = { onKind(k) },
                        label = { Text(k.label) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShiftYear(-1) }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year", tint = TextPrimary) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (summary.kind == SalesTaxYear.Kind.NYS_TAX_YEAR) "${summary.year}–${(summary.year + 1) % 100}" else "${summary.year}",
                        fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.titleLarge
                    )
                    Text(summary.label, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onShiftYear(1) }) { Icon(Icons.Default.ChevronRight, contentDescription = "Next year", tint = TextPrimary) }
            }
            if (summary.total.overrideApplied) {
                Text("Your typed totals are used for this year.", color = PrimaryGreen, style = MaterialTheme.typography.bodySmall)
            }
            YearTotalField("Taxable sales", "taxable", summary.total.taxable, summary.calculated.taxable, onLock)
            YearTotalField("Sales tax collected", "taxCollected", summary.total.taxCollected, summary.calculated.taxCollected, onLock)
            YearTotalField("Non-taxable sales", "nontaxable", summary.total.nontaxable, summary.calculated.nontaxable, onLock)

            HorizontalDivider()
            Text("Quarters (NYS filing periods)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            summary.segments.forEach { s ->
                Column {
                    Text("${s.label}  ·  due ${s.dueLabel}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Taxable ${usd(s.snapshot.taxable)}  ·  tax ${usd(s.snapshot.taxCollected)}  ·  non-taxable ${usd(s.snapshot.nontaxable)}",
                        color = TextSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider()
            Text("By jurisdiction and rate", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (summary.byJurisdiction.isEmpty()) {
                Text("No sales in this year yet.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            summary.byJurisdiction.forEach { r ->
                Column {
                    Text("${r.county}  ·  ${String.format(Locale.US, "%.3f", r.ratePercent).trimEnd('0').trimEnd('.')}%", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Taxable ${usd(r.taxable)}  ·  tax ${usd(r.taxCollected)}  ·  non-taxable ${usd(r.nontaxable)}",
                        color = TextSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onShareCsv, modifier = Modifier.weight(1f)) { Text("Share CSV", color = TextPrimary) }
                OutlinedButton(onClick = onSharePdf, modifier = Modifier.weight(1f)) { Text("Share PDF", color = TextPrimary) }
            }
        }
    }
}

@Composable
private fun YearTotalField(
    label: String,
    field: String,
    value: Double,
    calculated: Double,
    onLock: (String, String) -> Unit
) {
    var text by remember(value) { mutableStateOf(String.format(Locale.US, "%.2f", value)) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { onLock(field, text) }) { Text("Lock", color = TextPrimary) }
            OutlinedButton(onClick = { onLock(field, "") }) { Text("Unlock", color = TextPrimary) }
        }
        if (kotlin.math.abs(value - calculated) > 0.004) {
            Text("Calculated: ${usd(calculated)}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
