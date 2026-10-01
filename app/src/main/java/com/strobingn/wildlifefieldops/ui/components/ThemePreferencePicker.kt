package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.theme.ThemePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePreferencePicker(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = ThemePreference.entries
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Theme",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            selected.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Spacer(modifier = Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Theme: ${selected.label}" }
        ) {
            options.forEachIndexed { index, preference ->
                SegmentedButton(
                    selected = selected == preference,
                    onClick = { onSelect(preference) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {
                        Icon(
                            imageVector = preference.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            when (preference) {
                                ThemePreference.SYSTEM -> "System"
                                ThemePreference.LIGHT -> "Light"
                                ThemePreference.DARK -> "Dark"
                            }
                        )
                    }
                )
            }
        }
    }
}

private fun ThemePreference.icon(): ImageVector = when (this) {
    ThemePreference.SYSTEM -> Icons.Default.BrightnessAuto
    ThemePreference.LIGHT -> Icons.Default.LightMode
    ThemePreference.DARK -> Icons.Default.DarkMode
}
