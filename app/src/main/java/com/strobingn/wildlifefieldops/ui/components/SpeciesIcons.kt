package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ui.theme.*

/**
 * Species icon and tone mapping. Light theme uses darker greys so chips
 * meet WCAG AA on paper surfaces; dark theme keeps the original light greys.
 */
object SpeciesTheme {

    data class SpeciesStyle(
        val icon: ImageVector,
        val color: Color
    )

    fun forSpecies(species: String?): SpeciesStyle {
        val icon = when (species) {
            "Raccoon" -> Icons.Default.Pets
            "Grey Squirrel" -> Icons.Default.Forest
            "Red Squirrel" -> Icons.Default.Forest
            "Flying Squirrel" -> Icons.Default.Air
            "Bat" -> Icons.Default.NightsStay
            "Skunk" -> Icons.Default.Warning
            "Groundhog" -> Icons.Default.Grass
            "Bird" -> Icons.Default.Flight
            "Snake" -> Icons.Default.LinearScale
            "Opossum" -> Icons.Default.Pets
            "Rodent", "Mouse" -> Icons.Default.PestControl
            "Rat" -> Icons.Default.PestControl
            "Carpenter Bee" -> Icons.Default.BugReport
            else -> Icons.Default.HelpOutline
        }
        val color = when (species) {
            "Raccoon" -> pickTone(0xFFD8D8D8, 0xFF424242)
            "Grey Squirrel" -> pickTone(0xFFBEBEBE, 0xFF3C4043)
            "Red Squirrel" -> pickTone(0xFFA8A8A8, 0xFF5D4037)
            "Flying Squirrel" -> pickTone(0xFFC8C8C8, 0xFF455A64)
            "Bat" -> pickTone(0xFFE0E0E0, 0xFF37474F)
            "Skunk" -> pickTone(0xFFF0F0F0, 0xFF212121)
            "Groundhog" -> pickTone(0xFF969696, 0xFF33691E)
            "Bird" -> pickTone(0xFFCCCCCC, 0xFF455A64)
            "Snake" -> pickTone(0xFFB4B4B4, 0xFF4E342E)
            "Opossum" -> pickTone(0xFF9C9C9C, 0xFF4A4A4A)
            "Rodent", "Mouse" -> pickTone(0xFF888888, 0xFF4E342E)
            "Rat" -> pickTone(0xFF747474, 0xFF3E2723)
            "Carpenter Bee" -> pickTone(0xFFD0D0D0, 0xFF6D4C41)
            else -> TextSecondary
        }
        return SpeciesStyle(icon, color)
    }

    private fun pickTone(darkArgb: Long, lightArgb: Long): Color =
        Color(if (ThemeMode.isDark) darkArgb else lightArgb)
}

/**
 * Displays a species icon with its themed color.
 */
@Composable
fun SpeciesIcon(
    species: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val style = SpeciesTheme.forSpecies(species)
    Icon(
        imageVector = style.icon,
        contentDescription = species ?: "Unknown species",
        tint = tint ?: style.color,
        modifier = modifier
    )
}

/**
 * A small chip/badge showing the species name with themed background.
 */
@Composable
fun SpeciesChip(
    species: String?,
    modifier: Modifier = Modifier
) {
    val style = SpeciesTheme.forSpecies(species)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = style.color.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = species ?: "Unknown",
                style = MaterialTheme.typography.labelSmall,
                color = style.color
            )
        }
    }
}
