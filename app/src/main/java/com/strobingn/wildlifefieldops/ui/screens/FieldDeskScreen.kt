package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.field.FieldAction
import com.strobingn.wildlifefieldops.ai.field.FieldQuote
import com.strobingn.wildlifefieldops.ai.field.QuoteBuilder
import com.strobingn.wildlifefieldops.ai.field.SeasonRules
import com.strobingn.wildlifefieldops.ai.field.SignScorer
import com.strobingn.wildlifefieldops.ai.field.label
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldDeskScreen(
    onOpenDrawer: () -> Unit = {},
    onOpenWalkTalk: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var species by rememberSaveable { mutableStateOf("Raccoon") }
    var location by rememberSaveable { mutableStateOf("fascia") }
    var actionName by rememberSaveable { mutableStateOf(FieldAction.REPLACE_REPAIR.name) }
    var openings by rememberSaveable { mutableIntStateOf(2) }
    var width by rememberSaveable { mutableStateOf("4") }
    var height by rememberSaveable { mutableStateOf("4") }
    var grape by rememberSaveable { mutableStateOf(true) }
    var marble by rememberSaveable { mutableStateOf(false) }
    var rice by rememberSaveable { mutableStateOf(false) }
    var night by rememberSaveable { mutableStateOf(true) }
    var day by rememberSaveable { mutableStateOf(false) }
    var chirp by rememberSaveable { mutableStateOf(false) }
    var latrine by rememberSaveable { mutableStateOf(false) }
    var torn by rememberSaveable { mutableStateOf(true) }
    var notes by rememberSaveable { mutableStateOf("") }
    var copied by rememberSaveable { mutableStateOf(false) }

    val action = runCatching { FieldAction.valueOf(actionName) }.getOrDefault(FieldAction.REPLACE_REPAIR)
    val w = width.toDoubleOrNull() ?: 0.0
    val h = height.toDoubleOrNull() ?: 0.0
    val signs = SignScorer.score(
        grapeDroppings = grape,
        marbleDroppings = marble,
        riceDroppings = rice,
        holeInches = maxOf(w, h),
        night = night,
        day = day,
        chirp = chirp,
        latrine = latrine,
        tornFascia = torn,
        location = location
    )
    val quote = QuoteBuilder.build(
        species = species,
        action = action,
        location = location,
        openings = openings,
        widthIn = w,
        heightIn = h,
        notes = notes
    )
    val flags = SeasonRules.flags()

    Scaffold(containerColor = BackgroundDark) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Open menu")
                }
                Column(Modifier.weight(1f)) {
                    Text("Field desk", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Works with the radio off. Not a chatbot.", color = TextSecondary)
                }
            }

            Button(
                onClick = onOpenWalkTalk,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = BackgroundDark)
            ) {
                Text("Walk + quote", fontWeight = FontWeight.Bold)
            }

            Text("SEASON FLAGS", fontWeight = FontWeight.Bold, color = TextPrimary)
            flags.forEach { flag ->
                DeskCard {
                    Text(flag.title, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("${flag.level} · ${flag.detail}", color = TextSecondary)
                }
            }

            Text("SPECIES", fontWeight = FontWeight.Bold, color = TextPrimary)
            ChipRow(
                listOf("Raccoon", "Squirrel", "Bat", "Skunk", "Groundhog", "Bird"),
                species
            ) { species = it }

            Text("WHERE", fontWeight = FontWeight.Bold, color = TextPrimary)
            ChipRow(
                listOf("fascia", "gable", "ridge", "vent", "chimney", "soffit", "attic", "deck"),
                location
            ) { location = it }

            Text("JOB", fontWeight = FontWeight.Bold, color = TextPrimary)
            ChipRow(
                FieldAction.entries.map { it.name },
                actionName,
                labels = FieldAction.entries.associate { it.name to it.label() }
            ) { actionName = it }

            Text("SIGNS", fontWeight = FontWeight.Bold, color = TextPrimary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Grape droppings", grape) { grape = it }
                ToggleChip("Marble droppings", marble) { marble = it }
                ToggleChip("Rice / guano", rice) { rice = it }
                ToggleChip("Night", night) { night = it }
                ToggleChip("Day", day) { day = it }
                ToggleChip("Chirp", chirp) { chirp = it }
                ToggleChip("Latrine", latrine) { latrine = it }
                ToggleChip("Torn fascia", torn) { torn = it }
            }
            signs.forEach { hit ->
                Text("${hit.species} ${hit.score} — ${hit.why.joinToString()}", color = TextSecondary)
            }

            Text("OPENINGS TO REPLACE / REPAIR", fontWeight = FontWeight.Bold, color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { openings = (openings - 1).coerceAtLeast(0) }) { Text("-" ) }
                Text("$openings", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, color = TextPrimary)
                Button(onClick = { openings += 1 }) { Text("+") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    modifier = Modifier.weight(1f),
                    label = { Text("Width in") }
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    modifier = Modifier.weight(1f),
                    label = { Text("Height in") }
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Site notes") },
                minLines = 2
            )

            Text("QUOTE", fontWeight = FontWeight.Bold, color = TextPrimary)
            DeskCard {
                quote.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(line.name, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text(FieldQuote.money(line.total), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(FieldQuote.money(quote.total), fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Text("Tax included at ${String.format("%.3f", quote.tax / quote.subtotal.coerceAtLeast(0.01) * 100)}%", color = TextSecondary)
            }

            Text("WORK ORDER", fontWeight = FontWeight.Bold, color = TextPrimary)
            DeskCard {
                quote.workOrder.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", color = TextPrimary)
                }
            }

            if (quote.flags.isNotEmpty()) {
                Text("HOLD / WATCH", fontWeight = FontWeight.Bold, color = TextPrimary)
                DeskCard {
                    quote.flags.forEach { Text(it, color = TextPrimary) }
                }
            }

            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(quote.asText()))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = BackgroundDark)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied" else "Copy quote + work order", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    values: List<String>,
    selected: String,
    labels: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(labels[value] ?: value) }
            )
        }
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = on, onClick = { onChange(!on) }, label = { Text(label) })
}

@Composable
private fun DeskCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}
