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
 * Species icon and grayscale tone mapping.
 * Shape and luminance distinguish species without introducing color.
 */
object SpeciesTheme {

    data class SpeciesStyle(
        val icon: ImageVector,
        val color: Color
    )

    private val styles = mapOf(
        "Raccoon" to SpeciesStyle(Icons.Default.Pets, Color(0xFFD8D8D8)),
        "Grey Squirrel" to SpeciesStyle(Icons.Default.Forest, Color(0xFFBEBEBE)),
        "Red Squirrel" to SpeciesStyle(Icons.Default.Forest, Color(0xFFA8A8A8)),
        "Flying Squirrel" to SpeciesStyle(Icons.Default.Air, Color(0xFFC8C8C8)),
        "Bat" to SpeciesStyle(Icons.Default.NightsStay, Color(0xFFE0E0E0)),
        "Skunk" to SpeciesStyle(Icons.Default.Warning, Color(0xFFF0F0F0)),
        "Groundhog" to SpeciesStyle(Icons.Default.Grass, Color(0xFF969696)),
        "Bird" to SpeciesStyle(Icons.Default.Flight, Color(0xFFCCCCCC)),
        "Snake" to SpeciesStyle(Icons.Default.LinearScale, Color(0xFFB4B4B4)),
        "Opossum" to SpeciesStyle(Icons.Default.Pets, Color(0xFF9C9C9C)),
        "Rodent" to SpeciesStyle(Icons.Default.PestControl, Color(0xFF888888)),
        "Mouse" to SpeciesStyle(Icons.Default.PestControl, Color(0xFF888888)),
        "Rat" to SpeciesStyle(Icons.Default.PestControl, Color(0xFF747474)),
        "Carpenter Bee" to SpeciesStyle(Icons.Default.BugReport, Color(0xFFD0D0D0)),
        "Other" to SpeciesStyle(Icons.Default.HelpOutline, Color(0xFFAAAAAA)),
    )

    fun forSpecies(species: String?): SpeciesStyle {
        return styles[species] ?: SpeciesStyle(
            Icons.Default.HelpOutline,
            TextSecondary
        )
    }
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
