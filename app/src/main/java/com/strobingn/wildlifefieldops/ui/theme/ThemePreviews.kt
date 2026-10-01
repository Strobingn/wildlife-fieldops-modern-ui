package com.strobingn.wildlifefieldops.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.model.PreferredContact
import com.strobingn.wildlifefieldops.pricing.MoneyField
import com.strobingn.wildlifefieldops.ui.components.BrandMark
import com.strobingn.wildlifefieldops.ui.components.FieldCard
import com.strobingn.wildlifefieldops.ui.components.FieldTopBar
import com.strobingn.wildlifefieldops.ui.components.OverrideCaption
import com.strobingn.wildlifefieldops.ui.components.SpeciesChip
import com.strobingn.wildlifefieldops.ui.components.StatPillCard
import com.strobingn.wildlifefieldops.ui.components.StatusChip
import com.strobingn.wildlifefieldops.ui.components.ThemePreferencePicker
import com.strobingn.wildlifefieldops.ui.screens.JobCustomerSection

@Composable
private fun PreviewTheme(dark: Boolean, content: @Composable () -> Unit) {
    WildlifeFieldOpsTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Preview(name = "Dashboard · light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dashboard · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardThemePreview() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    PreviewTheme(dark = dark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 40)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Good morning", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("FieldOps", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary)
            }
            Surface(shape = FieldShapes.hero, color = Color.Transparent) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(GradientStart, GradientMid, PrimaryContainer)),
                            FieldShapes.hero
                        )
                        .padding(18.dp)
                ) {
                    Text("Today", color = OnPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text("3 jobs scheduled", color = OnPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    StatusChip(text = "1 overdue", color = StatusUrgent)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPillCard("Active", 2, icon = Icons.Default.PlayCircle, color = AccentBlue, modifier = Modifier.weight(1f), onClick = {})
                StatPillCard("Pending", 4, icon = Icons.Default.Schedule, color = StatusPending, modifier = Modifier.weight(1f), onClick = {})
                StatPillCard("Done", 8, icon = Icons.Default.CheckCircle, color = SuccessGreen, modifier = Modifier.weight(1f), onClick = {})
            }
            Surface(shape = FieldShapes.button, color = PrimaryGreen, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "New job",
                    color = OnPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Preview(name = "Settings · light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Settings · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsThemePreview() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    PreviewTheme(dark = dark) {
        Column {
            FieldTopBar(title = "Settings", onBack = {})
            Card(
                colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                ThemePreferencePicker(
                    selected = if (dark) ThemePreference.DARK else ThemePreference.LIGHT,
                    onSelect = {},
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Preview(name = "Jobs · light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Jobs · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JobListThemePreview() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    PreviewTheme(dark = dark) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldTopBar(title = "Jobs", onBack = {})
            FieldCard(onClick = {}, accentColor = StatusInProgress) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Bat exclusion — attic", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Hudson Valley Customer", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    StatusChip(text = "IN PROGRESS", color = StatusInProgress)
                }
            }
            FieldCard(onClick = {}, accentColor = StatusPending) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Squirrel one-way door", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Cornwall, NY", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StatusChip(text = "PENDING", color = StatusPending)
                        Text("Pending sync", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeciesChip("Raccoon")
                SpeciesChip("Bat")
                SpeciesChip("Grey Squirrel")
            }
        }
    }
}

@Preview(name = "Manual chip · light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Manual chip · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ManualPricingChipPreview() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    PreviewTheme(dark = dark) {
        Column(Modifier.padding(16.dp)) {
            Text("Job total", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text("$1,850.00", style = MaterialTheme.typography.headlineSmall, color = PrimaryGreen, fontWeight = FontWeight.Bold)
            OverrideCaption(
                field = MoneyField(calculated = 1620.0, override = 1850.0),
                onReset = {}
            )
        }
    }
}

@Preview(name = "Invoice paper · light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Invoice paper · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InvoicePaperPreview() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    PreviewTheme(dark = dark) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldTopBar(title = "Invoice", onBack = {})
            Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Screen chrome follows the theme.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = PaperWhite),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Wildlife Whisperer LLC", color = OnPaper, fontWeight = FontWeight.Bold)
                    Text("Invoice INV-1042", color = OnPaper, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("PDF paper stays white in both themes.", color = OnPaper.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Total  $1,850.00", color = OnPaper, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(name = "Dialog · light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dialog · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DialogThemePreview() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    PreviewTheme(dark = dark) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Clear All Data?", color = TextPrimary) },
            text = { Text("This will permanently delete all jobs, customers, inspections, and photos.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = {}) { Text("Delete Everything", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = {}) { Text("Cancel", color = TextSecondary) } },
            containerColor = BackgroundCard
        )
    }
}

@Preview(name = "Job customer · light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Job customer · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JobCustomerSectionThemePreview() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    PreviewTheme(dark = dark) {
        Column(Modifier.padding(16.dp)) {
            JobCustomerSection(
                draft = JobCustomerDraft(
                    customerId = "cust-1",
                    name = "Hudson Valley Customer",
                    companyName = "Willow Properties",
                    phone = "845-555-0142",
                    email = "ops@example.com",
                    address = "12 Willow Ave",
                    city = "Cornwall",
                    state = "NY",
                    zipCode = "12518",
                    preferredContact = PreferredContact.PHONE
                ),
                onDraftChange = {},
                searchQuery = "",
                onSearchQueryChange = {},
                matches = emptyList(),
                onPickCustomer = {},
                onNewCustomer = {}
            )
        }
    }
}

@Preview(name = "Live HUD · light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Live HUD · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LiveCaptureOverlayPreview() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    PreviewTheme(dark = dark) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(16.dp)
        ) {
            Surface(color = OverlayScrim, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Job · Hudson Valley Customer", color = OverlayOnDark, fontWeight = FontWeight.SemiBold)
                    Text("12 Willow Ave · Bat exclusion", color = OverlayOnDarkMuted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    StatusChip(text = "ACCEPT", color = PrimaryGreen)
                }
            }
        }
    }
}
